package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.BlobId

/**
 * Platform-specific scheduler for blob upload and download jobs. Platform implementations
 * enqueue work on WorkManager (Android) or run it immediately in a coroutine (iOS foreground).
 *
 * Defined here (in `feature/attachment/datamanager`) so [LocalBlobStore] callers can trigger
 * uploads without depending on `feature/sync/data`. Implementations live in
 * `feature/sync/data` (androidMain / iosMain) because they use sync-layer machinery.
 */
interface UploadScheduler {
  /** Enqueue an upload job for a [BlobId] that is in LOCAL_ONLY state. */
  fun scheduleUpload(blobId: BlobId)

  /** Enqueue a download job for a [BlobId] that is in REMOTE_ONLY state. */
  fun scheduleDownload(blobId: BlobId)

  /** Enqueue a delete job for a [BlobId] whose `blob_object` row has `deleted=1`. */
  fun scheduleDelete(blobId: BlobId)

  /**
   * Cancel all in-flight and pending blob jobs. Called on sign-out so uploads don't run
   * against a stale auth token.
   */
  fun cancelAll()

  /**
   * Whether the sync engine should proactively schedule downloads for REMOTE_ONLY rows at
   * sign-in. Mobile platforms prefetch eagerly so attachments are ready offline; the web
   * scheduler returns `false` to preserve OPFS quota and bandwidth — REMOTE_ONLY rows are
   * downloaded lazily via [scheduleDownload] when the user opens them.
   */
  val prefetchRemoteOnly: Boolean get() = true
}
