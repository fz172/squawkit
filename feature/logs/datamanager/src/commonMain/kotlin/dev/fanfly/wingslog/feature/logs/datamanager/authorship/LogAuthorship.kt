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
   * Nothing to attest. Either the log names no technician, or it carries no provenance — a manual
   * hand-typed entry (no `source_uid`), or a revision written before authorship was recorded.
   * Deliberately NOT reported as "assigned": absence of proof is not proof of third-party entry.
   */
  data object Unknown : LogAuthorship

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
  val sourceUid = technician?.source_uid?.takeIf { it.isNotBlank() }
    ?: return LogAuthorship.Unknown // hand-typed technician: no account to attest against
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
