package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.awaitAsList
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.blob.AttachmentRefs
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Deletes synced tombstone rows older than [retention], and reclaims the local blobs the dead
 * records referenced. Called once on app start.
 *
 * The `dirty=0` predicate is essential: a long-offline device must keep its un-pushed deletes
 * around so they reach Firestore on reconnect. Without that guard, GC would resurrect the docs.
 *
 * See docs/storage/deletion_gc_design.html §5.
 */
@OptIn(ExperimentalTime::class)
class TombstoneGc(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
  /**
   * Absent in hosts with no blob storage (they simply have no rows to reclaim); when present, this
   * is the only thing that ever frees the device's copy of an attachment on a deleted record.
   */
  private val blobs: LocalBlobStore? = null,
  private val retention: Duration = RETENTION,
) {

  suspend fun runOnce(now: Instant = Clock.System.now()) {
    val cutoffMs = (now - retention).toEpochMilliseconds()
    // Blobs first: the payloads about to be deleted are the only record of which blobs belonged to
    // these entities. Crashing between the two is harmless — the next run re-derives the same set.
    reclaimBlobs(cutoffMs)
    writeLock.withLock { db.schemaQueries.gcTombstones(cutoffMs) }
  }

  /**
   * Frees the device's copy of every blob referenced only by tombstones that are about to be
   * purged. Local reclamation only — the canonical bytes were already removed by the server's
   * cascade when the delete was pushed, and are never this client's to delete (§5.5).
   */
  private suspend fun reclaimBlobs(cutoffMs: Long) {
    val blobs = blobs ?: return

    val doomed = db.schemaQueries.selectPurgeableTombstones(cutoffMs)
      .awaitAsList()
    if (doomed.isEmpty()) return

    val condemned = mutableSetOf<BlobId>()
    // The trees to re-check for live references before anything is dropped. Whole accounts, not the
    // dead records' own scopes: a copy can put the same attachment id on a record in a *different*
    // aircraft, and that record's blob must survive its twin's purge just the same.
    val roots = mutableSetOf<String>()

    for (row in doomed) {
      roots += userRootOf(row.scope_path)
      if (row.collection == CollectionKind.Aircraft) {
        // Blobs are aircraft-scoped, so a deleted aircraft takes its whole blob prefix with it
        // (§5.2). This is also what reclaims blobs no surviving payload names — the orphans of a
        // cascade whose child tombstones this device never saw. Built from the aircraft's own
        // scope and id, never an id from elsewhere (the #204 lesson).
        condemned += blobs.idsInScopePrefix("${row.scope_path}aircraft/${row.id}/%")
      }
      try {
        condemned += AttachmentRefs.blobIdsIn(row.collection, row.payload)
      } catch (e: Exception) {
        // A payload we cannot read is a payload we cannot reason about. Skip its blobs — leaking
        // bytes is recoverable, dropping a file some live record still shows is not.
        Logger.e(throwable = e) {
          "Could not decode ${row.collection.wireName} ${row.id} for blob reclamation; keeping its blobs"
        }
      }
    }
    if (condemned.isEmpty()) return

    condemned -= stillReferenced(roots)
    if (condemned.isEmpty()) return

    blobs.purgeLocal(condemned)
    Logger.i { "TombstoneGc reclaimed ${condemned.size} local blob(s)" }
  }

  /** `/users/u1/aircraft/a1/` → `/users/u1/`; anything else is left as-is and vetoes only itself. */
  private fun userRootOf(scopePath: String): String {
    val segments = scopePath.trim('/')
      .split('/')
    return if (segments.size >= 2 && segments[0] == "users") "/users/${segments[1]}/" else scopePath
  }

  /**
   * Blob ids that a *live* record anywhere in [roots] still names. Attachment ids are
   * per-attachment, but a copy or duplicate can put the same id on two records, and a blob one of
   * them still shows must survive the other's purge.
   */
  private suspend fun stillReferenced(roots: Set<String>): Set<BlobId> {
    val referenced = mutableSetOf<BlobId>()
    for (root in roots) {
      val live = db.schemaQueries.selectLivePayloadsInScopePrefix("$root%")
        .awaitAsList()
      for (row in live) {
        try {
          referenced += AttachmentRefs.blobIdsIn(row.collection, row.payload)
        } catch (e: Exception) {
          Logger.e(throwable = e) {
            "Could not decode a live ${row.collection.wireName} payload while checking blob references"
          }
        }
      }
    }
    return referenced
  }

  companion object {
    /**
     * How long a local tombstone survives.
     *
     * Must agree with the server sweep's `TOMBSTONE_RETENTION_DAYS`
     * (backend/firebase/functions/.env) — the two ends purge the same delete, and a client that
     * forgets a tombstone the server still holds (or the reverse) is how a deleted record comes
     * back from the dead. `TombstoneRetentionAgreementTest` fails the build if they drift apart.
     */
    val RETENTION: Duration = 30.days
  }
}
