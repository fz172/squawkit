package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Finishes a released (`deleted=1`) blob on this device by hard-deleting its local row. Called by
 * the scheduler during the startup scan and after [LocalBlobStore.delete].
 *
 * **It never deletes the remote object, own tree included** (docs/ai/task_population_design.md
 * §8.3). It used to, for own-tree blobs, and that is unsafe once one document can sit on several
 * records: an AI suggestion's source manual is the same blob on every task that cites it, and a
 * device that let go of it on one task would have destroyed it for the rest. The server collects
 * the canonical bytes once no live record names them (`onRecordBlobsReleased`, then the daily
 * sweep as backstop), which is also what always happened for a member's foreign-hosted blobs.
 *
 * Holding no Storage client is the guarantee: there is nothing here that could delete one.
 */
class BlobDeleteDriver(
  private val blobs: LocalBlobStore,
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
    writeLock.withLock { db.schemaQueries.hardDeleteBlob(id.value) }
    log.i { "hard-deleted blob row ${id.value}; the server collects the remote object" }
    return true
  }

  companion object {
    private const val TAG = "BlobDeleteDriver"
  }
}
