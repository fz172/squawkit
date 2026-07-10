package dev.fanfly.wingslog.feature.sync.data

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import kotlinx.coroutines.flow.Flow

/**
 * Opens Firestore snapshot subscriptions decoded into [RemoteEntity] batches, feeding the
 * [PullListener] apply path (LWW, tombstones, cursor advance). Two shapes:
 *  - [observeCollection] — a filtered collection query, the common case;
 *  - [observeSingleDoc] — one document, for scopes a member may `get` but not `list` (the shared
 *    aircraft doc, docs/sharing §5.2).
 *
 * The returned flows are cold; collecting opens the listener, cancelling collection detaches it.
 * Implemented by [FirestorePullSubscription]; mirrors the SyncWriter/RemoteFetcher interface pattern.
 */
interface PullSubscription {
  /**
   * Docs in `(kind, scope)` newer than [sinceRemoteTsMs]. The watermark is inclusive-of-equality at
   * the query layer (`>`), so it must be the highest `remoteTsMs` already applied — never one less.
   */
  fun observeCollection(
    kind: CollectionKind,
    scope: EntityScope,
    sinceRemoteTsMs: Long?,
  ): Flow<List<RemoteEntity>>

  /** A single document `(kind, scope, id)` — one `DocumentReference`, no `list` permission needed. */
  fun observeSingleDoc(
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
  ): Flow<List<RemoteEntity>>
}
