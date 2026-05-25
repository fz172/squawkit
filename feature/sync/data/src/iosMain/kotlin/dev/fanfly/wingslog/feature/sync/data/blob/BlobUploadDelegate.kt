package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.darwin.NSObject

/**
 * [NSURLSessionTaskDelegateProtocol] that handles upload task completion events for the
 * background URLSession used by [UrlSessionUploadScheduler].
 *
 * The blob ID is stored in [NSURLSessionTask.taskDescription] so it survives process kills:
 * when iOS relaunches the app to deliver completion events, the delegate can identify the
 * blob without any in-memory state.
 */
@OptIn(ExperimentalForeignApi::class)
class BlobUploadDelegate(
  private val blobs: LocalBlobStore,
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) : NSObject(), NSURLSessionTaskDelegateProtocol {

  private val log = Logger.withTag(TAG)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun URLSession(
    session: NSURLSession,
    task: NSURLSessionTask,
    didCompleteWithError: NSError?,
  ) {
    val blobIdStr = task.taskDescription ?: run {
      log.w { "background upload task completed with no taskDescription; ignoring" }
      return
    }
    val blobId = BlobId(blobIdStr)
    val httpResponse = task.response as? NSHTTPURLResponse
    val statusCode = httpResponse?.statusCode ?: -1L
    val success = didCompleteWithError == null && statusCode in 200L..299L

    log.d { "background upload complete for ${blobId.value}: status=$statusCode error=${didCompleteWithError?.localizedDescription}" }

    scope.launch {
      val row = db.schemaQueries.selectBlobById(blobId.value).executeAsOneOrNull()
      if (row == null) {
        log.w { "no blob_object row for ${blobId.value} on completion; skipping" }
        return@launch
      }
      writeLock.withLock { db.schemaQueries.clearResumeUrl(blobId.value) }
      if (row.remote_state != RemoteState.Uploading) {
        log.w { "blob ${blobId.value} is ${row.remote_state.wireName} on completion; skipping transition" }
        return@launch
      }
      if (success) {
        val remotePath = row.remote_path ?: "${row.scope_path.trim('/')}/blobs/${blobId.value}"
        blobs.markUploaded(blobId, remotePath)
        log.i { "background upload succeeded for ${blobId.value}" }
      } else {
        blobs.markFailedTransient(blobId)
        log.w { "background upload failed for ${blobId.value}; will retry" }
      }
    }
  }

}

private const val TAG = "BlobUploadDelegate"
