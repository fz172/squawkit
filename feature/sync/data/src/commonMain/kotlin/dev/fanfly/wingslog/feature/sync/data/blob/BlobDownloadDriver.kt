package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

/**
 * One-shot download of a single `blob_object` row from `REMOTE_ONLY` → `SYNCED`.
 *
 * **Own-tree** blobs fetch a Firebase Storage download URL and pull the bytes directly.
 * **Foreign-hosted** (shared-aircraft) blobs go through the [AttachmentBroker] `streamBlob` proxy,
 * because `storage.rules` deny cross-account reads and the proxy is the only authorized door
 * (design §9.2). Either way the bytes flow into [LocalBlobStore.installDownloaded], which verifies
 * the sha256 and writes to disk.
 *
 * Returns `true` on terminal success or permanent failure; `false` on transient failure.
 *
 * A missing remote object (Storage 404) is a **permanent** failure and moves the row to
 * `REMOTE_MISSING` (#426) — see [isRemoteObjectMissing].
 */
class BlobDownloadDriver(
  private val blobs: LocalBlobStore,
  private val storage: FirebaseStorage,
  private val httpClient: HttpClient,
  private val auth: FirebaseAuth,
  private val broker: AttachmentBroker,
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
    log.d { "Remote path is $remotePath" }
    if (remotePath == null) {
      log.w { "download skipped: ${id.value} has null remote_path" }
      return true
    }

    val location = BlobLocation.of(ref)
    val foreign = location?.isForeign(auth.currentUser?.uid) == true

    val bytes = try {
      if (foreign) {
        broker.download(location.ownerUid, location.aircraftId, id.value)
      } else {
        val url = storage.reference(remotePath)
          .getDownloadUrl()
        log.d { "Download url is $url" }
        httpClient.get(url)
          .readRawBytes()
      }
    } catch (e: Exception) {
      // A 404 is an answer, not an outage: the object is gone and no amount of retrying brings it
      // back (#426). Left as transient it kept WorkManager rescheduling this job for the life of
      // the install, and made every export wait out ensureLocal's full timeout on the same blob.
      if (e.isRemoteObjectMissing()) {
        blobs.markRemoteMissing(id, e)
        log.w(e) { "download permanent failure for ${id.value}: no object at $remotePath" }
        return true
      }
      log.w(e) { "download transient failure for ${id.value}; will retry" }
      return false
    }

    val result = blobs.installDownloaded(id, bytes, ref.sha256)
    return result.fold(
      onSuccess = {
        log.i { "downloaded ${id.value}" }
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
