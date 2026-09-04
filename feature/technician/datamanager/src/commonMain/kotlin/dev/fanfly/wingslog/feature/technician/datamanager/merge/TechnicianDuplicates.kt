package dev.fanfly.wingslog.feature.technician.datamanager.merge

import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Technician

/**
 * Duplicate detection for the technician roster (design §7.4).
 *
 * Once the mechanics a user hand-typed before sharing join as members, each shows up twice: the
 * stale manual row (user-global, in their own collection) and the live mirror (per-thing, from
 * the share). This groups those so the user can reconcile them.
 *
 * Everything here is pure — the matching rules are fiddly enough to be worth testing on their own,
 * away from Firestore and Compose.
 */

/** What the user is being asked to do about one cluster of look-alike rows. */
enum class DuplicateResolution {
  /**
   * Two manual rows sharing a non-empty certificate number. A certificate number is unique to a
   * person, so this is safe to pre-check: keep the richer row, delete the other. Logs already hold
   * snapshots, so nothing historical is lost.
   */
  MERGE_MANUAL,

  /**
   * A manual row that looks like a share member. The member's mirror is the source of truth and
   * survives; the hand-typed copy is deleted. Logs hold their own snapshots, so the technician
   * recorded on already-signed work is untouched.
   */
  MERGE_INTO_MEMBER,

  /**
   * Two mirrors with the same certificate number. Two members are two distinct accounts, so this is
   * never a merge — it's surfaced as a likely typo for a human to sort out.
   */
  WARN_MIRROR_CONFLICT,
}

/**
 * One cluster of rows that look like the same person.
 *
 * [autoSafe] means the match key is strong enough to pre-check in the review sheet. It is true only
 * for a certificate-number match: names collide, so a name-only match is always confirmation-gated.
 * Nothing is ever applied without the user acting — [autoSafe] only decides the initial checkbox.
 */
data class DuplicateGroup(
  val resolution: DuplicateResolution,
  /** The row to keep: the richer manual row, or the member's mirror. */
  val keep: Technician,
  /** The rows superseded by [keep]. A merge deletes them; a warning applies nothing. */
  val duplicates: List<Technician>,
  val autoSafe: Boolean,
)

/**
 * Groups look-alike rows across the user's manual roster, their own self-record, and the mirrors
 * visible to them.
 *
 * [manual] is the user's technician collection *minus* their self-record; [self] is that record;
 * [mirrors] are the published member profiles (each carrying its owner's uid in `source_uid`).
 */
fun findDuplicates(
  manual: List<Technician>,
  mirrors: List<Technician>,
  self: Technician? = null,
): List<DuplicateGroup> {
  val groups = mutableListOf<DuplicateGroup>()
  val claimed = mutableSetOf<String>()

  // 1. Manual ↔ SELF. Run first: you are the authority on your own name. A user who hand-typed
  //    themselves as a technician before the app bootstrapped their self-record now has both, and
  //    that copy should collapse into the real profile rather than be merged into some member who
  //    happens to share the name. The self-record is always the keeper — never deleted.
  if (self != null) {
    val matches = manual.filter { it.id != self.id && it.matches(self) }
    if (matches.isNotEmpty()) {
      matches.forEach { claimed += it.id }
      groups += DuplicateGroup(
        resolution = DuplicateResolution.MERGE_MANUAL,
        keep = self,
        duplicates = matches,
        autoSafe = matches.all { it.sharesACertNumberWith(self) },
      )
    }
  }

  // 2. Manual ↔ mirror. The member's mirror is the source of truth, so a manual row that matches
  //    one collapses into them rather than into another manual row.
  for (mirror in mirrors) {
    val matches = manual.filter { it.id !in claimed && it.matches(mirror) }
    if (matches.isEmpty()) continue
    matches.forEach { claimed += it.id }
    groups += DuplicateGroup(
      resolution = DuplicateResolution.MERGE_INTO_MEMBER,
      keep = mirror,
      duplicates = matches,
      // Only a certificate-number match is strong enough to pre-check.
      autoSafe = matches.all { it.sharesACertNumberWith(mirror) },
    )
  }

  // 3. Manual ↔ manual, by certificate number. Auto-safe: a cert number identifies one person.
  //    A row is filed under EVERY number it carries, because one person can hold several
  //    certifications and two rows sharing any one of them are that person twice (#684).
  manual.filter { it.id !in claimed }
    .flatMap { row -> row.certKeys().map { it to row } }
    .groupBy({ it.first }, { it.second })
    .forEach { (_, filed) ->
      val rows = filed.filter { it.id !in claimed }
        .distinctBy { it.id }
      if (rows.size < 2) return@forEach
      val keep = rows.maxWith(RICHEST)
      rows.forEach { claimed += it.id }
      groups += DuplicateGroup(
        resolution = DuplicateResolution.MERGE_MANUAL,
        keep = keep,
        duplicates = rows - keep,
        autoSafe = true,
      )
    }

  // 4. Manual ↔ manual, by name. Proposed only — two people genuinely can share a name, so this
  //    never pre-checks.
  manual.filter { it.id !in claimed }
    .groupBy { it.nameKey() to it.resolvedCertTypeKey() }
    .forEach { (_, rows) ->
      if (rows.size < 2) return@forEach
      // A certificate number is definitive: two rows carrying *different* ones are two different
      // people, however alike their names read. Only a name match unopposed by conflicting
      // certificates is a candidate.
      if (rows.map { it.certKeys() }.filter { it.isNotEmpty() }.distinct().size > 1) return@forEach
      val keep = rows.maxWith(RICHEST)
      rows.forEach { claimed += it.id }
      groups += DuplicateGroup(
        resolution = DuplicateResolution.MERGE_MANUAL,
        keep = keep,
        duplicates = rows - keep,
        autoSafe = false,
      )
    }

  // 5. Mirror ↔ mirror sharing a certificate number. Never merged — two members are two people.
  mirrors.flatMap { row -> row.certKeys().map { it to row } }
    .groupBy({ it.first }, { it.second })
    .forEach { (_, rows) ->
      if (rows.size < 2) return@forEach
      groups += DuplicateGroup(
        resolution = DuplicateResolution.WARN_MIRROR_CONFLICT,
        keep = rows.first(),
        duplicates = rows.drop(1),
        autoSafe = false,
      )
    }

  return groups
}

/**
 * A stable identity for *which* duplicates these are — not how many.
 *
 * Dismissing the review has to mean "I've seen these", not "never mention duplicates again". The
 * signature is stored when the user dismisses, and compared against the current one: add a new
 * look-alike later and the signature changes, so the prompt returns. Order-independent, so a
 * reshuffled roster doesn't spuriously re-prompt.
 */
fun List<DuplicateGroup>.signature(): String =
  map { group ->
    (listOf(group.keep.id) + group.duplicates.map { it.id }).sorted()
      .joinToString(",")
  }
    .sorted()
    .joinToString("|")

/**
 * The keeper's certifications, plus any kind only a duplicate carried (#684).
 *
 * **The union is the whole point of deriving roles from credentials.** The A&P who also services
 * the user's car is one contact holding two certifications; merging by taking only the keeper's
 * would silently drop the second, which is the same information loss that pushing users toward one
 * record per domain would have caused.
 *
 * The keeper wins on a kind both rows carry: it is the richer row by construction, and a duplicate
 * number for the same credential is the typo the merge exists to resolve.
 */
fun DuplicateGroup.mergedCertifications(): List<Certification> {
  val kept = keep.resolvedCertifications()
  val keptTypes = kept.map { it.type }
    .toSet()
  return kept + duplicates.flatMap { it.resolvedCertifications() }
    .filterNot { it.type in keptTypes }
    .distinctBy { it.type }
}

/** Certificate number match, else name. Callers decide how much confirmation each deserves. */
private fun Technician.matches(other: Technician): Boolean {
  if (certKeys().isNotEmpty() && other.certKeys().isNotEmpty()) {
    return sharesACertNumberWith(other)
  }
  return nameKey().isNotEmpty() && nameKey() == other.nameKey()
}

/**
 * Every certificate number the person carries, as match keys.
 *
 * A blank certificate number is never a key. The marquee case — an owner doing FAR 43 preventive
 * maintenance with no certificate — is a name-only entry, and treating two blanks as equal would
 * collapse every uncertificated technician into a single person.
 *
 * Reads through `resolvedCertifications`, so a row written before #684 still compares on the single
 * certificate it stored.
 */
private fun Technician.certKeys(): Set<String> =
  resolvedCertifications()
    .mapNotNull {
      it.number.trim()
        .uppercase()
        .takeIf { number -> number.isNotEmpty() }
    }
    .toSet()

/** One shared number is enough: a certificate number identifies a person, not a credential. */
private fun Technician.sharesACertNumberWith(other: Technician): Boolean =
  certKeys().intersect(other.certKeys()).isNotEmpty()

private fun Technician.nameKey(): String =
  name.trim()
    .lowercase()
    .replace(WHITESPACE, " ")

/**
 * The set of credentials the person holds, as a grouping key.
 *
 * Two same-named rows holding different credentials are not obviously one person — an A&P Bob and
 * an electrician Bob plausibly are two — so they are not proposed as a name match.
 */
private fun Technician.resolvedCertTypeKey(): String =
  resolvedCertifications().map { it.type }
    .sorted()
    .joinToString(",")

/**
 * "Most complete" row, per §7.4. Technician carries no edited-at timestamp, so completeness is all
 * we have to go on: how many credentials it carries, then how much of each is filled in, then
 * expiry, and finally the fuller name — without that last tiebreak "Bob" and "Bob Squarepants"
 * score identically and the keeper is decided by list order.
 */
private val RICHEST = compareBy<Technician>(
  { it.certKeys().size },
  { it.name.isNotBlank() },
  { it.resolvedCertifications().any { certification -> certification.expiration != null } },
  { it.name.trim().length },
)

private val WHITESPACE = Regex("\\s+")
