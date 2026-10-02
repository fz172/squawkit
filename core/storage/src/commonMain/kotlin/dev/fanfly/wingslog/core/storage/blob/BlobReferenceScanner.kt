package dev.fanfly.wingslog.core.storage.blob

import app.cash.sqldelight.async.coroutines.awaitAsList
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.EntityRef
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Which blobs live records still name (docs/ai/task_population_design.md §8.3). Extracted from
 * `TombstoneGc` so the release path asks the same question the same way: a copy, a duplicate, or
 * one document cited by several tasks can put one attachment id on many records, and a blob any
 * of them still shows must survive the others letting go of it.
 *
 * The device's view, which is what it can decide on; the server makes the same check against
 * Firestore before deleting the canonical bytes (`onRecordBlobsReleased`).
 */
class BlobReferenceScanner(private val db: WingsLogDatabase) {

  data class References(
    val referenced: Set<BlobId>,
    /**
     * How many live payloads would not decode. Their claims are unknown, so a caller that must not
     * lose a file treats any of them as naming every blob.
     */
    val undecodable: Int,
  ) {
    /** Whether [id] is safe to let go of: named by no live record, and nothing went unread. */
    fun isFree(id: BlobId): Boolean = undecodable == 0 && id !in referenced
  }

  /**
   * Blob ids named by live records under any of [roots] (scope path prefixes, such as
   * `/users/u1/`), leaving out [excluding]: the record whose edit is dropping the blob still has
   * its old payload in the table until the edit is written.
   */
  suspend fun referencedUnder(
    roots: Set<String>,
    excluding: EntityRef? = null
  ): References {
    val referenced = mutableSetOf<BlobId>()
    var undecodable = 0
    for (root in roots) {
      val live = db.schemaQueries.selectLivePayloadsInScopePrefix("$root%")
        .awaitAsList()
      for (row in live) {
        if (excluding != null && row.collection == excluding.collection && row.id == excluding.id) continue
        try {
          referenced += AttachmentRefs.blobIdsIn(row.collection, row.payload)
        } catch (e: Exception) {
          undecodable++
          Logger.e(throwable = e) {
            "Could not decode a live ${row.collection.wireName} payload while checking blob references"
          }
        }
      }
    }
    return References(referenced, undecodable)
  }

  companion object {
    /** `/users/u1/thing/a1/` → `/users/u1/`; anything else is returned as-is and checks only itself. */
    fun userRootOf(scopePath: String): String {
      val segments = scopePath.trim('/')
        .split('/')
      return if (segments.size >= 2 && segments[0] == "users") "/users/${segments[1]}/" else scopePath
    }
  }
}
