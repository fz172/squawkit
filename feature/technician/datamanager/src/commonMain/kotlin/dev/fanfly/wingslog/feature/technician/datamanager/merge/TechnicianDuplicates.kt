package dev.fanfly.wingslog.feature.technician.datamanager.merge

import dev.fanfly.wingslog.aircraft.Technician

/**
 * Duplicate detection for the technician roster (design §7.4).
 *
 * Once the mechanics a user hand-typed before sharing join as members, each shows up twice: the
 * stale manual row (user-global, in their own collection) and the live mirror (per-aircraft, from
 * the share). This groups those so the user can reconcile them.
 *
 * Everything here is pure — the matching rules are fiddly enough to be worth testing on their own,
 * away from Firestore and Compose.
 */

/** What the user is being asked to do about one cluster of look-alike rows. */
enum class DuplicateResolution {
  /**
   * Two manual rows sharing a non-empty certificate number. A certificate number is unique to a
   * person, so this is safe to pre-check: keep the richer row, tombstone the other. Logs already
   * hold snapshots, so nothing historical is lost.
   */
  MERGE_MANUAL,

  /**
   * A manual row that looks like a share member. The mirror is the source of truth, but the manual
   * row is *aliased*, never deleted — see [Technician.superseded_by_uid].
   */
  ALIAS_TO_MEMBER,

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
  /** The rows superseded by [keep] — tombstoned or aliased depending on [resolution]. */
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
  //    that copy should collapse into the real profile rather than be aliased to some member who
  //    happens to share the name. The self-record is always the keeper — never tombstoned, never
  //    aliased away.
  if (self != null) {
    val matches = manual.filter { it.id != self.id && it.matches(self) }
    if (matches.isNotEmpty()) {
      matches.forEach { claimed += it.id }
      groups += DuplicateGroup(
        resolution = DuplicateResolution.MERGE_MANUAL,
        keep = self,
        duplicates = matches,
        autoSafe = matches.all { it.certKey() != null && it.certKey() == self.certKey() },
      )
    }
  }

  // 2. Manual ↔ mirror. The mirror is the source of truth for a member, so a manual row that
  //    matches one should alias to them rather than be merged into another manual row.
  for (mirror in mirrors) {
    val matches = manual.filter { it.id !in claimed && it.matches(mirror) }
    if (matches.isEmpty()) continue
    matches.forEach { claimed += it.id }
    groups += DuplicateGroup(
      resolution = DuplicateResolution.ALIAS_TO_MEMBER,
      keep = mirror,
      duplicates = matches,
      // Only a certificate-number match is strong enough to pre-check.
      autoSafe = matches.all { it.certKey() != null && it.certKey() == mirror.certKey() },
    )
  }

  // 3. Manual ↔ manual, by certificate number. Auto-safe: a cert number identifies one person.
  val remaining = manual.filter { it.id !in claimed }
  remaining.groupBy { it.certKey() }
    .forEach { (certKey, rows) ->
      if (certKey == null || rows.size < 2) return@forEach
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
      if (rows.mapNotNull { it.certKey() }.distinct().size > 1) return@forEach
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
  mirrors.groupBy { it.certKey() }
    .forEach { (certKey, rows) ->
      if (certKey == null || rows.size < 2) return@forEach
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
 * Drops the manual rows the user has already aliased to a member whose mirror is present here.
 *
 * This is what makes a manual↔member merge *visible*: the alias only records the decision, and
 * every surface that lists technicians has to honour it or the duplicate simply stays on screen.
 *
 * Scoped to the mirrors actually present, never global — that is the whole reason the merge aliases
 * instead of deleting. Manual rows are user-global while mirrors are per-aircraft, so on an aircraft
 * this member isn't on, their hand-typed row is still the only way to pick them (§7.4).
 */
fun List<Technician>.withoutAliasedTo(mirrors: List<Technician>): List<Technician> {
  val present = mirrors.mapTo(mutableSetOf()) { it.source_uid }
  return filterNot { it.superseded_by_uid.isNotBlank() && it.superseded_by_uid in present }
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

/** Certificate number match, else name. Callers decide how much confirmation each deserves. */
private fun Technician.matches(other: Technician): Boolean {
  val cert = certKey()
  val otherCert = other.certKey()
  if (cert != null && otherCert != null) return cert == otherCert
  return nameKey().isNotEmpty() && nameKey() == other.nameKey()
}

/**
 * The certificate number as a match key, or null when there isn't one.
 *
 * A blank certificate number is never a key. The marquee case — an owner doing FAR 43 preventive
 * maintenance with no certificate — is a name-only entry, and treating two blanks as equal would
 * collapse every uncertificated technician into a single person.
 */
private fun Technician.certKey(): String? =
  cert_number.trim()
    .uppercase()
    .takeIf { it.isNotEmpty() }

private fun Technician.nameKey(): String =
  name.trim()
    .lowercase()
    .replace(WHITESPACE, " ")

/** Both the enum and the legacy string field, so a pre-enum row still compares. */
private fun Technician.resolvedCertTypeKey(): String =
  certificate_type.name.takeIf { it != NONE_CERT } ?: cert_type.trim().uppercase()

/**
 * "Most complete" row, per §7.4. Technician carries no edited-at timestamp, so completeness is all
 * we have to go on: certificate first, then how many fields are filled, then expiry, and finally
 * the fuller name — without that last tiebreak "Bob" and "Bob Squarepants" score identically and
 * the keeper is decided by list order.
 */
private val RICHEST = compareBy<Technician>(
  { it.cert_number.isNotBlank() },
  { listOf(it.name, it.cert_number).count { field -> field.isNotBlank() } },
  { it.cert_expiration != null },
  { it.name.trim().length },
)

private const val NONE_CERT = "CERTIFICATE_TYPE_NONE"
private val WHITESPACE = Regex("\\s+")
