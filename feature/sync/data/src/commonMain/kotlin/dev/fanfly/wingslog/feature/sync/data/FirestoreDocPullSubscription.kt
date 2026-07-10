package dev.fanfly.wingslog.feature.sync.data

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Snapshot subscription on a single document, for scopes a member may `get` but not `list` — namely
 * the shared aircraft doc, where a query over the host's `aircraft` collection is a denied `list`
 * (docs/sharing §5.2). Feeds the same [PullListener] path as [FirestorePullSubscription] (LWW,
 * tombstones, and cursor advance all unchanged); the only difference is one `DocumentReference`
 * instead of a filtered collection query.
 *
 * The returned flow is cold; collecting it opens the listener, cancelling collection detaches it.
 */
class FirestoreDocPullSubscription(private val firestore: FirebaseFirestore) {

  private val log = Logger.withTag(TAG)

  fun observe(
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
  ): Flow<List<RemoteEntity>> =
    FirestoreRefs.document(firestore, kind, scope, id).snapshots.map { snap ->
      if (!snap.exists) return@map emptyList()
      // decodeRemoteEntity drops docs whose server timestamp is still pending (our own un-acked
      // writes echoed via the local cache), mirroring the collection path.
      val remote = runCatching { decodeRemoteEntity(snap) }.getOrElse { e ->
        log.w(e) { "skipping malformed shared doc ${scope.toPath()}$id" }
        null
      }
      if (remote != null) listOf(remote) else emptyList()
    }

  companion object {
    private const val TAG = "FirestoreDocPullSubscription"
  }
}
