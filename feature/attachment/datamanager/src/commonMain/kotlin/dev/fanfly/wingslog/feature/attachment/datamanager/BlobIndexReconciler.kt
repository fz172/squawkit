package dev.fanfly.wingslog.feature.attachment.datamanager

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.PostWriteHook
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Observes every entity written to the local store and registers a REMOTE_ONLY placeholder row in
 * [LocalBlobStore] for each attachment whose sha256 is populated but whose blob is not yet
 * present on this device.
 *
 * This is what makes newly-pulled entities' attachments visible to [AttachmentManager] without
 * requiring a full blob download first — the REMOTE_ONLY row is the signal to the download driver
 * that a file exists on the server and should be fetched.
 */
class BlobIndexReconciler(
  private val blobs: LocalBlobStore,
  private val coroutineScope: CoroutineScope,
  private val uploadScheduler: UploadScheduler? = null,
) : PostWriteHook {

  private val log = Logger.withTag(TAG)

  override fun onEntityWritten(
    kind: CollectionKind,
    scope: EntityScope,
    payload: ByteArray
  ) {
    val attachments = try {
      when (kind) {
        CollectionKind.MaintenanceLog -> MaintenanceLog.ADAPTER.decode(payload).attachments
        CollectionKind.MaintenanceTask -> MaintenanceTask.ADAPTER.decode(payload).attachments
        CollectionKind.Squawk -> Squawk.ADAPTER.decode(payload).attachments
        // No `attachments` field on these kinds today. Deliberately exhaustive (no `else`) so
        // adding one to a proto in the future forces a decision here instead of silently
        // skipping reconciliation, the way CollectionKind.Squawk was skipped before this fix.
        CollectionKind.Aircraft,
        CollectionKind.MaintenanceOverview,
        CollectionKind.Technician,
        CollectionKind.UserInfo,
        CollectionKind.FeatureLab,
          -> return
      }
    } catch (e: Exception) {
      log.e(e) { "failed to decode ${kind.wireName} payload for blob index reconciliation" }
      return
    }

    val prefetch = uploadScheduler?.prefetchRemoteOnly ?: false
    for (att in attachments) {
      if (att.id.isBlank() || att.sha256.isBlank()) continue
      val blobId = BlobId(att.id)
      coroutineScope.launch {
        blobs.upsertRemoteOnly(
          id = blobId,
          sha256 = att.sha256,
          sizeBytes = att.size_bytes,
          contentType = att.mime_type.ifBlank { null },
          scope = scope,
        )
        if (prefetch) uploadScheduler.scheduleDownload(blobId)
      }
    }
  }

  companion object {
    private const val TAG = "BlobIndexReconciler"
  }
}
