package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

/**
 * One-shot download of a single `blob_object` row from `REMOTE_ONLY` → `SYNCED`.
 * Gets the download URL from Firebase Storage, fetches bytes via HTTP, then
 * calls [LocalBlobStore.installDownloaded] which verifies the sha256 and writes to disk.
 *
 * Returns `true` on terminal success or permanent failure; `false` on transient failure.
 */
class BlobDownloadDriver(
  private val blobs: LocalBlobStore,
  private val storage: FirebaseStorage,
  private val httpClient: HttpClient,
) {

  private val log = Logger.withTag(TAG)

  suspend fun runOnce(id: BlobId): Boolean {
    val ref = blobs.get(id)
    if (ref == null) {
      log.w { "download skipped: no row for ${id.value}" }
      return true
    }
    if (ref.remoteState != RemoteState.RemoteOnly) {
      log.v { "download skipped: ${id.value} is ${ref.remoteState.wireName}" }
      return true
    }
    val remotePath = ref.remotePath
    if (remotePath == null) {
      log.w { "download skipped: ${id.value} has null remote_path" }
      return true
    }

    val bytes = try {
      val url = storage.reference(remotePath).getDownloadUrl()
      httpClient.get(url).readRawBytes()
    } catch (e: Exception) {
      log.w(e) { "download transient failure for ${id.value}; will retry" }
      return false
    }

    val result = blobs.installDownloaded(id, bytes, ref.sha256)
    return result.fold(
      onSuccess = {
        log.i { "downloaded ${id.value} from $remotePath" }
        true
      },
      onFailure = { e ->
        log.e(e) { "download integrity failure for ${id.value}; discarding bytes" }
        false  // row stays REMOTE_ONLY — caller retries
      }
    )
  }

  companion object {
    private const val TAG = "BlobDownloadDriver"
  }
}
