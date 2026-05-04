package dev.fanfly.wingslog.feature.sync.data

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope

/**
 * One-shot read of every document in a `(kind, scope)` Firestore collection.
 *
 * Used by [HydrationRunner] to populate the local table after first sign-in. The platform impl
 * issues `firestore.collection(...).get()` and decodes each doc to a [RemoteEntity]; commonMain
 * then performs all the SQL writes inside a single transaction.
 */
interface RemoteFetcher {
  suspend fun fetchAll(
    kind: CollectionKind,
    scope: EntityScope,
  ): List<RemoteEntity>
}
