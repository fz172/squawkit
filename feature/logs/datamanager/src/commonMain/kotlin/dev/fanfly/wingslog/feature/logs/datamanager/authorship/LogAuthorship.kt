package dev.fanfly.wingslog.feature.logs.datamanager.authorship

import dev.fanfly.wingslog.aircraft.MaintenanceLog

/**
 * Whether the technician named on a log actually wrote it (design §7.5).
 *
 * On a shared aircraft the payload is opaque bytes and any member may write any bytes, so a member
 * can create a log and name a *different* member as its technician. The snapshot alone can't tell
 * "B signed their own work" from "A attributed the work to B".
 *
 * The envelope's `writer_uid` can, because rules reject a write whose `writer_uid` isn't the caller
 * — it is backend-attested and unforgeable. Comparing it against the snapshot's `source_uid` (the
 * first-party profile the technician was picked from) is what distinguishes the two.
 *
 * Note the limitation this inherits from §7.5: `writer_uid` attests who wrote the *row*, not who
 * performed the work, and reflects the latest revision. A durable per-log signature that survives
 * later third-party edits is the countersign fast-follow.
 */
sealed interface LogAuthorship {
  /**
   * Nothing to attest: the log names no technician, or the revision predates `writer_uid`. Silent in
   * the UI — deliberately NOT reported as "assigned" or "unverified", because absence of proof is
   * not proof of anything, and a row written before the field existed accuses nobody.
   */
  data object Unknown : LogAuthorship

  /**
   * The technician was typed in by hand and belongs to no account ([Technician.source_uid] is
   * empty), so there is nothing to attest the name against — anyone can type any name. This is a
   * real, reportable fact, and distinct from [Unknown]: we know *why* it can't be verified.
   */
  data class Unverifiable(val technicianName: String) : LogAuthorship

  /** The named technician wrote this revision themselves. */
  data class SelfSigned(val technicianName: String) : LogAuthorship

  /** Someone else wrote this revision and named [technicianName] as the technician. */
  data class Assigned(
    val authorName: String?,
    val technicianName: String,
  ) : LogAuthorship
}

/**
 * Resolves the authorship of one log revision.
 *
 * [writerUid] is the envelope author of the revision (null on rows predating the field).
 * [nameForUid] resolves an account uid to a display name — the share roster, plus the caller
 * themselves — and may return null for a member who has published no mirror.
 */
fun MaintenanceLog.authorship(
  writerUid: String?,
  nameForUid: (String) -> String?,
): LogAuthorship {
  val technicianName = technician?.name?.takeIf { it.isNotBlank() }
    ?: return LogAuthorship.Unknown
  // Hand-typed: no account behind the name, so nothing can attest it — say so rather than let a
  // typed name sit there looking as settled as a signed one. Checked before writer_uid, because it
  // holds regardless of who wrote the row.
  val sourceUid = technician?.source_uid?.takeIf { it.isNotBlank() }
    ?: return LogAuthorship.Unverifiable(technicianName)
  if (writerUid.isNullOrBlank()) return LogAuthorship.Unknown

  return if (writerUid == sourceUid) {
    LogAuthorship.SelfSigned(technicianName)
  } else {
    LogAuthorship.Assigned(
      authorName = nameForUid(writerUid),
      technicianName = technicianName,
    )
  }
}
