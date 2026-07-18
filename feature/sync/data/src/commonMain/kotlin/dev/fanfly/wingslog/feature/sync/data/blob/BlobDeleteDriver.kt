package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.storage.FirebaseStorage

/**
 * Cleans up a tombstoned (`deleted=1`) blob: removes the remote Firebase Storage object if one
 * exists, then hard-deletes the local row. Called by the scheduler during startup scan and after
 * [LocalBlobStore.delete] is invoked.
 *
 * Safe to call multiple times — if the remote object is already gone, Firebase Storage returns a
 * 404 which we treat as success (the object is gone either way).
 */
class BlobDeleteDriver(
  private val blobs: LocalBlobStore,
  private val storage: FirebaseStorage,
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  private val log = Logger.withTag(TAG)

  suspend fun runOnce(id: BlobId): Boolean {
    val ref = blobs.get(id)
    if (ref == null) {
      log.v { "delete skipped: no row for ${id.value}" }
      return true
    }
    if (!ref.deleted) {
      log.w { "delete skipped: ${id.value} is not tombstoned" }
      return true
    }

    // Delete the remote object whenever one could exist. remotePath is populated for every state
    // that has been (or is being) uploaded — Synced, Uploading, AND RemoteOnly. RemoteOnly is the
    // easy one to miss: it's a blob known from a synced record whose bytes were never downloaded to
    // this device (e.g. after a reinstall), so it very much exists in gs:// and must be removed.
    // Only LocalOnly carries a null path, and it has nothing in Storage to delete.
    val remotePath = ref.remotePath
    if (remotePath != null) {
      try {
        storage.reference(remotePath)
          .delete()
        log.i { "deleted remote object $remotePath" }
      } catch (e: Exception) {
        if (isNotFound(e)) {
          log.i { "remote object $remotePath already gone" }
        } else {
          log.w(e) { "transient failure deleting remote object $remotePath; will retry" }
          return false
        }
      }
    }

    writeLock.withLock { db.schemaQueries.hardDeleteBlob(id.value) }
    log.i { "hard-deleted blob row ${id.value}" }
    return true
  }

  private fun isNotFound(e: Exception): Boolean =
    e.message?.contains("404") == true || e.message?.contains(
      "not found",
      ignoreCase = true
    ) == true

  companion object {
    private const val TAG = "BlobDeleteDriver"
  }
}
