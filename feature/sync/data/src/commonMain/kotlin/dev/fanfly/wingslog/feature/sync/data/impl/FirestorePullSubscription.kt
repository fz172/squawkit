package dev.fanfly.wingslog.feature.sync.data.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.feature.sync.data.PullSubscription
import dev.fanfly.wingslog.feature.sync.data.RemoteEntity
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.fromMilliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Firestore implementation of [PullSubscription].
 *
 * Both shapes decode via [decodeRemoteEntity], which drops documents whose server timestamp is
 * still pending (our own un-acked writes echoed via the local cache) rather than passing them to
 * [PullListener].
 */
class FirestorePullSubscription(private val firestore: FirebaseFirestore) :
  PullSubscription {

  private val log = Logger.withTag(TAG)

  override fun observeCollection(
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
          LAST_UPDATE_TIMESTAMP,
          Direction.ASCENDING
        )

    val query =
      if (sinceRemoteTsMs != null) {
        val watermark = Timestamp.fromMilliseconds(sinceRemoteTsMs.toDouble())
        baseQuery.where { LAST_UPDATE_TIMESTAMP greaterThan watermark }
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

  override fun observeSingleDoc(
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
  ): Flow<List<RemoteEntity>> =
    FirestoreRefs.document(firestore, kind, scope, id).snapshots.map { snap ->
      if (!snap.exists) return@map emptyList()
      val remote = runCatching { decodeRemoteEntity(snap) }.getOrElse { e ->
        log.w(e) { "skipping malformed shared doc ${scope.toPath()}$id" }
        null
      }
      if (remote != null) listOf(remote) else emptyList()
    }

  companion object {
    private const val TAG = "FirestorePullSubscription"

    // Must match the field name kotlinx.serialization produces from SyncDocWire.lastUpdateTimestamp.
    private const val LAST_UPDATE_TIMESTAMP = "lastUpdateTimestamp"
  }
}
