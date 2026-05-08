package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.feature.attachment.datamanager.BlobFilesystem
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.FirebaseStorage

/**
 * One-shot upload of a single `blob_object` row from `LOCAL_ONLY` → `UPLOADING` → `SYNCED`.
 * Idempotent: if the row is already `SYNCED` or `UPLOADING` (from a prior attempt that died
 * mid-flight), returns `true` without re-uploading.
 *
 * Returns `true` on terminal success or permanent failure; `false` on transient failure so the
 * caller (WorkManager / foreground scheduler) knows to retry.
 */
class BlobUploadDriver(
  private val blobs: LocalBlobStore,
  private val storage: FirebaseStorage,
  private val auth: FirebaseAuth,
  private val fs: BlobFilesystem,
) {

  private val log = Logger.withTag(TAG)

  suspend fun runOnce(id: BlobId): Boolean {
    val ref = blobs.get(id)
    if (ref == null) {
      log.w { "upload skipped: no row for ${id.value}" }
      return true
    }

    when (ref.remoteState) {
      RemoteState.Synced -> {
        log.v { "upload skipped: ${id.value} already SYNCED" }
        return true
      }
      RemoteState.Uploading -> {
        // Previous attempt left the row stuck in UPLOADING (process died). Reset and retry.
        blobs.markFailedTransient(id)
      }
      RemoteState.RemoteOnly -> {
        log.w { "upload skipped: ${id.value} is REMOTE_ONLY — should not be scheduled for upload" }
        return true
      }
      RemoteState.LocalOnly -> { /* proceed */ }
    }

    val user = auth.currentUser
    if (user == null || user.isAnonymous) {
      log.i { "upload deferred: ${id.value} — no permanent auth user" }
      return false
    }

    val bytes = try {
      fs.read(ref.relativePath)
    } catch (e: Exception) {
      log.e(e) { "upload failed permanently: cannot read local file for ${id.value}" }
      blobs.markFailedPermanent(id, e)
      return true  // permanent — don't retry
    }

    blobs.markUploading(id)

    val remotePath = ref.remotePath
      ?: "${ref.scope.toPath().trim('/')}/blobs/${id.value}"

    return try {
      storage.reference(remotePath).putData(Data(bytes))
      blobs.markUploaded(id, remotePath)
      log.i { "uploaded ${id.value} → $remotePath" }
      true
    } catch (e: Exception) {
      log.w(e) { "upload transient failure for ${id.value}; will retry" }
      blobs.markFailedTransient(id)
      false
    }
  }

  companion object {
    private const val TAG = "BlobUploadDriver"
  }
}
