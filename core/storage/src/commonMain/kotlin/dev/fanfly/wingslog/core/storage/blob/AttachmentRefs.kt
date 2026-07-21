package dev.fanfly.wingslog.core.storage.blob

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.storage.CollectionKind

/**
 * The attachments an entity payload references.
 *
 * The payload is the reference list — nothing else on the device records which blobs a record owns —
 * so both ends of a blob's life read it through here: `BlobIndexReconciler` registers a row for each
 * attachment it finds, and `dev.fanfly.wingslog.core.storage.TombstoneGc` reclaims them when the
 * record's tombstone is purged. One `when`, so the two can never disagree about which kinds carry
 * attachments.
 *
 * Throws if [payload] does not decode. Callers decide what a corrupt payload means for them; this
 * never guesses.
 */
object AttachmentRefs {

  fun of(kind: CollectionKind, payload: ByteArray): List<Attachment> = when (kind) {
    CollectionKind.MaintenanceLog -> MaintenanceLog.ADAPTER.decode(payload).attachments
    CollectionKind.MaintenanceTask -> MaintenanceTask.ADAPTER.decode(payload).attachments
    CollectionKind.Squawk -> Squawk.ADAPTER.decode(payload).attachments
    // No `attachments` field on these kinds today. Deliberately exhaustive (no `else`) so adding one
    // to a proto forces a decision here instead of silently skipping both reconciliation and GC —
    // the way CollectionKind.Squawk was skipped before this was one list.
    CollectionKind.Aircraft,
    CollectionKind.MaintenanceOverview,
    CollectionKind.Technician,
    CollectionKind.UserInfo,
    CollectionKind.FeatureLab,
    CollectionKind.Subscription,
    CollectionKind.SharedAircraftRef,
      -> emptyList()
  }

  /**
   * The blob ids [of] names. Deliberately does *not* require a sha256: an attachment whose bytes
   * landed on disk before the sha reached the payload still has a local row, and skipping it would
   * leak that file past every sweep that could have reclaimed it.
   */
  fun blobIdsIn(kind: CollectionKind, payload: ByteArray): List<BlobId> =
    of(kind, payload)
      .filter { it.id.isNotBlank() }
      .map { BlobId(it.id) }
}
