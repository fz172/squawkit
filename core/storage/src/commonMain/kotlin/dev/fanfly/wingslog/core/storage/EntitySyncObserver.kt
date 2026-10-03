package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether a local row has reached Firestore yet. A server check that reads Firestore, such as the AI
 * backend confirming a Thing exists before it runs (docs/ai/task_population_design.md §5.3), refuses
 * a row this device has written but not pushed; the caller waits for it here first.
 */
class EntitySyncObserver(
  private val db: WingsLogDatabase,
  private val ioContext: CoroutineContext = storageIoContext,
) {

  /** True once the row is pushed, false while it waits, null when there is no such row. */
  fun observeSynced(kind: CollectionKind, scope: EntityScope, id: String): Flow<Boolean?> =
    db.schemaQueries.selectDirtyFlag(collection = kind, scope = scope.toPath(), id = id)
      .asFlow()
      .mapToOneOrNull(ioContext)
      .map { dirty -> dirty?.not() }
      .distinctUntilChanged()

  /**
   * Waits until the row is pushed, for at most [timeout]. True when it is, false on timeout or when
   * there is no such row (nothing local to wait for: the server decides).
   */
  suspend fun awaitSynced(kind: CollectionKind, scope: EntityScope, id: String, timeout: Duration): Boolean =
    withTimeoutOrNull(timeout) { observeSynced(kind, scope, id).first { it != false } } == true
}
