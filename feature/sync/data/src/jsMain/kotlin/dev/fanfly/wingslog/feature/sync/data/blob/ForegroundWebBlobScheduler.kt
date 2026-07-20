package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Web [UploadScheduler] that runs blob jobs as foreground coroutines for the lifetime of the
 * open tab. There is no service-worker background transfer in v1 — uploads, downloads, and
 * deletes that don't terminate before the tab closes resume on next visit via
 * [dev.fanfly.wingslog.feature.sync.data.SyncEngine.schedulePendingBlobs] startup scan.
 *
 * Per `docs/web/web_attachments_design.md` §5:
 *  - Duplicate schedules are coalesced per operation via the in-flight sets.
 *  - On transient driver failure the job retries with exponential backoff
 *    (30s, 60s, 120s, …, capped at 60min) while the tab stays open.
 *  - [cancelAll] cancels the scope and recreates it; the in-flight sets are cleared so that
 *    a subsequent sign-in can re-queue work.
 *
 * Downloads are lazy (open-triggered) via the [prefetchRemoteOnly]` = false` default it inherits.
 */
class ForegroundWebBlobScheduler(
  private val uploadDriver: BlobUploadDriver,
  private val downloadDriver: BlobDownloadDriver,
  private val deleteDriver: BlobDeleteDriver,
) : UploadScheduler {

  private val log = Logger.withTag(TAG)

  private val mutex = Mutex()
  private val uploadsInFlight = mutableSetOf<String>()
  private val downloadsInFlight = mutableSetOf<String>()
  private val deletesInFlight = mutableSetOf<String>()

  private var scope = newScope()

  override fun scheduleUpload(blobId: BlobId) {
    scope.launch {
      if (!claim(uploadsInFlight, blobId)) return@launch
      try {
        runWithBackoff("upload", blobId) { uploadDriver.runOnce(blobId) }
      } finally {
        release(uploadsInFlight, blobId)
      }
    }
  }

  override fun scheduleDownload(blobId: BlobId) {
    scope.launch {
      if (!claim(downloadsInFlight, blobId)) return@launch
      try {
        runWithBackoff("download", blobId) { downloadDriver.runOnce(blobId) }
      } finally {
        release(downloadsInFlight, blobId)
      }
    }
  }

  override fun scheduleDelete(blobId: BlobId) {
    scope.launch {
      if (!claim(deletesInFlight, blobId)) return@launch
      try {
        runWithBackoff("delete", blobId) { deleteDriver.runOnce(blobId) }
      } finally {
        release(deletesInFlight, blobId)
      }
    }
  }

  override fun cancelAll() {
    scope.cancel()
    scope = newScope()
    // Coroutines holding the in-flight slots have been cancelled, so the release() in their
    // finally blocks won't run reliably. Clear the sets explicitly.
    uploadsInFlight.clear()
    downloadsInFlight.clear()
    deletesInFlight.clear()
  }

  private suspend fun runWithBackoff(
    op: String,
    blobId: BlobId,
    block: suspend () -> Boolean,
  ) {
    var attempt = 0
    while (true) {
      val terminal = try {
        block()
      } catch (e: Exception) {
        log.w(e) { "$op ${blobId.value} threw; treating as transient" }
        false
      }
      if (terminal) return
      val wait = backoffDelayMs(attempt)
      log.i { "$op ${blobId.value} transient failure; retrying in ${wait}ms" }
      delay(wait)
      attempt++
    }
  }

  private suspend fun claim(set: MutableSet<String>, id: BlobId): Boolean =
    mutex.withLock { set.add(id.value) }

  private suspend fun release(set: MutableSet<String>, id: BlobId) {
    mutex.withLock { set.remove(id.value) }
  }

  private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  companion object {
    private const val TAG = "ForegroundWebBlobScheduler"
    private const val MIN_BACKOFF_MS = 30_000L
    private const val MAX_BACKOFF_MS = 60L * 60_000L  // 60 min

    internal fun backoffDelayMs(attempt: Int): Long {
      // 30s, 60s, 120s, 240s … capped at 60min.
      val safeAttempt = attempt.coerceIn(0, 30)
      val doubled = MIN_BACKOFF_MS shl safeAttempt
      return if (doubled <= 0 || doubled > MAX_BACKOFF_MS) MAX_BACKOFF_MS else doubled
    }
  }
}
