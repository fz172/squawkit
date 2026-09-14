package dev.fanfly.wingslog.core.storage.blob

import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.blob.AttachmentRefs.of

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

  fun of(kind: CollectionKind, payload: ByteArray): List<Attachment> =
    when (kind) {
      CollectionKind.MaintenanceLog -> MaintenanceLog.ADAPTER.decode(payload).attachments
      CollectionKind.MaintenanceTask -> MaintenanceTask.ADAPTER.decode(payload).attachments
      CollectionKind.Squawk -> Squawk.ADAPTER.decode(payload).attachments
      // The record's one blob is the raw file, embedded as an Attachment for exactly this reason.
      CollectionKind.DataLog -> listOfNotNull(DataLog.ADAPTER.decode(payload).raw_file)
      // No `attachments` field on these kinds today. Deliberately exhaustive (no `else`) so adding one
      // to a proto forces a decision here instead of silently skipping both reconciliation and GC —
      // the way CollectionKind.Squawk was skipped before this was one list.
      CollectionKind.Thing,
      CollectionKind.Comment,
      CollectionKind.MaintenanceOverview,
      CollectionKind.Technician,
      CollectionKind.UserInfo,
      CollectionKind.DeveloperOptions,
      CollectionKind.Subscription,
      CollectionKind.SharedAircraftRef,
      CollectionKind.NotificationSettings,
        -> emptyList()
    }

  /**
   * The blob ids [of] names. Deliberately does *not* require a sha256: an attachment whose bytes
   * landed on disk before the sha reached the payload still has a local row, and skipping it would
   * leak that file past every sweep that could have reclaimed it.
   *
   * A `DATA_LOG` attachment is a reference to a DataLog record and owns nothing; its bytes belong
   * to that record's own `raw_file`, which must outlive the reference.
   */
  fun blobIdsIn(kind: CollectionKind, payload: ByteArray): List<BlobId> =
    of(kind, payload)
      .filter { it.type != AttachmentType.ATTACHMENT_TYPE_DATA_LOG && it.id.isNotBlank() }
      .map { BlobId(it.id) }
}
