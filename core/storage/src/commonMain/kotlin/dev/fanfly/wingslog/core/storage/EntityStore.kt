package dev.fanfly.wingslog.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * Reactive key-value store for one [CollectionKind]. The contract is local-only — implementations
 * never talk to Firestore. The sync engine (M3) reads `dirty=1` rows out of band and pushes them
 * to the cloud, but managers and view models only ever interact with this interface.
 *
 * Behavior contract (asserted by `EntityStoreContract` test base in C6):
 * - [put] upserts with `dirty=1`, `updated_at = now`. Active flows emit synchronously.
 * - [delete] writes a tombstone (`deleted=1, dirty=1`); flows emit a list with the row absent.
 * - [observeAll] returns rows where `deleted=0`, ordered by `updated_at DESC`.
 * - Two scopes are isolated: writes under one [EntityScope] are invisible to another.
 */
interface EntityStore<T : Any> {
  val kind: CollectionKind

  fun observeAll(scope: EntityScope): Flow<List<StorageEntity<T>>>

  fun observe(id: String, scope: EntityScope): Flow<StorageEntity<T>?>

  suspend fun put(id: String, value: T, scope: EntityScope)

  suspend fun delete(id: String, scope: EntityScope)
}
