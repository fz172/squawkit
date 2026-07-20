package dev.fanfly.wingslog.core.storage.blob


/**
 * Platform-specific scheduler for blob upload and download jobs. Platform implementations
 * enqueue work on WorkManager (Android) or run it immediately in a coroutine (iOS foreground).
 *
 * Defined here (in `core:storage`, beside [LocalBlobStore]) so callers can trigger uploads
 * without depending on `feature/sync/data`. Implementations live in `feature/sync/data`
 * (androidMain / iosMain) because they use sync-layer machinery.
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
   * Whether the sync engine should proactively schedule downloads for REMOTE_ONLY rows at sign-in.
   * `false` on every platform: attachment bytes are fetched lazily via [scheduleDownload] when the
   * user opens an attachment (`AttachmentManager.ensureLocal`), to preserve storage and bandwidth
   * rather than pulling every blob up front. A platform may override to `true` to prefetch for
   * offline availability.
   */
  val prefetchRemoteOnly: Boolean get() = false
}
