package dev.fanfly.wingslog.feature.sync.data

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.fromMilliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Opens a Firestore snapshot subscription for `(kind, scope)` filtered to docs newer than the
 * caller's cursor watermark, decoded into [RemoteEntity] batches.
 *
 * The watermark is **inclusive of equality** at the query layer (`>`), so the cursor stored in
 * `sync_cursor.last_seen_remote` must be the highest `remoteTsMs` already applied — never one
 * less. Documents whose server timestamp is still pending (our own un-acked writes echoed via the
 * local cache) are dropped here rather than passed on to [PullListener].
 *
 * The returned flow is cold; collecting it opens the listener, cancelling collection detaches it.
 */
class FirestorePullSubscription(private val firestore: FirebaseFirestore) {

  private val log = Logger.withTag(TAG)

  fun observe(
    kind: CollectionKind,
    scope: EntityScope,
    sinceRemoteTsMs: Long?,
  ): Flow<List<RemoteEntity>> {
    val baseQuery =
      FirestoreRefs.collection(
        firestore,
        kind,
        scope
      )
        .orderBy(
          UPDATED_AT,
          Direction.ASCENDING
        )

    val query =
      if (sinceRemoteTsMs != null) {
        val watermark = Timestamp.fromMilliseconds(sinceRemoteTsMs.toDouble())
        baseQuery.where { UPDATED_AT greaterThan watermark }
      } else {
        baseQuery
      }

    return query.snapshots.map { snap ->
      snap.documentChanges.mapNotNull { change ->
        runCatching { decodeRemoteEntity(change.document) }.getOrElse { e ->
          log.w(e) { "skipping malformed sync doc ${change.document.id}" }
          null
        }
      }
    }
  }

  companion object {
    private const val TAG = "FirestorePullSubscription"
    // Must match the field name kotlinx.serialization produces from SyncDocWire.lastUpdateTimestamp.
    private const val UPDATED_AT = "lastUpdateTimestamp"
  }
}
