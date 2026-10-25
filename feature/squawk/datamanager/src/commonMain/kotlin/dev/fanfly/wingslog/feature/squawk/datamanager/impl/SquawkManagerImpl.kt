package dev.fanfly.wingslog.feature.squawk.datamanager.impl

import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.resolve
import dev.fanfly.wingslog.core.storage.resolveNow
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason

class SquawkManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  private val commentManager: CommentManager,
  storeFactory: EntityStoreFactory,
) : SquawkManager {

  private val store: EntityStore<Squawk> =
    storeFactory.create(CollectionKind.Squawk)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeSquawks(thingId: ThingId): Flow<List<Squawk>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          flowOf(emptyList())
        } else {
          store.observeAll(scope)
            .map { rows -> rows.map { it.value } }
            .catch { e ->
              logger.w(e) { "Error observing squawks for thing ${thingId.value}" }
              emit(emptyList())
            }
        }
      }

  override suspend fun addSquawk(
    thingId: ThingId,
    squawk: Squawk,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val withId =
        if (squawk.id.isEmpty()) squawk.copy(id = generateRandomId()) else squawk
      store.put(withId.id, withId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding squawk" } }

  override suspend fun updateSquawk(
    thingId: ThingId,
    squawk: Squawk,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      store.put(squawk.id, squawk, scope)
      true
    }.onFailure { logger.w(it) { "Error updating squawk ${squawk.id}" } }

  override suspend fun deleteSquawk(
    thingId: ThingId,
    squawkId: SquawkId,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      store.delete(squawkId.value, scope)
      // The thread goes with its record; see CommentManager.deleteThread.
      commentManager.deleteThread(
        CommentTarget(thingId.value, squawkId.value, CommentParentKind.SQUAWK),
      )
      true
    }.onFailure { logger.w(it) { "Error deleting squawk ${squawkId.value}" } }

  override suspend fun markAddressed(
    thingId: ThingId,
    squawkIds: List<SquawkId>,
    logId: MaintenanceLogId,
  ): Result<Unit> = runCatching {
    val scope = scopeResolver.resolveNow(thingId)
    val allSquawks = store.observeAll(scope)
      .firstOrNull()
      ?.associateBy { it.id } ?: emptyMap()
    squawkIds.forEach { squawkId ->
      val existing = allSquawks[squawkId.value]?.value ?: return@forEach
      store.put(squawkId.value, existing.copy(addressed_by_log_id = logId.value), scope)
    }
  }.onFailure { logger.w(it) { "Error marking squawks addressed: ${squawkIds.map { it.value }}" } }

  override suspend fun dismissSquawk(
    thingId: ThingId,
    squawkId: SquawkId,
    reason: SquawkDismissReason,
  ): Result<Unit> = runCatching {
    val scope = scopeResolver.resolveNow(thingId)
    val existing = store.observeAll(scope)
      .firstOrNull()
      ?.associateBy { it.id }
      ?.get(squawkId.value)?.value
      ?: error("Squawk ${squawkId.value} not found")
    store.put(
      squawkId.value,
      existing.copy(
        dismiss_reason = reason,
        dismissed_at = Clock.System.now()
          .toWireInstant(),
      ),
      scope,
    )
  }.onFailure { logger.w(it) { "Error dismissing squawk ${squawkId.value}" } }

  override suspend fun reopenSquawk(
    thingId: ThingId,
    squawkId: SquawkId,
  ): Result<Unit> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val existing = store.observeAll(scope)
        .firstOrNull()
        ?.associateBy { it.id }
        ?.get(squawkId.value)?.value
        ?: error("Squawk ${squawkId.value} not found")
      store.put(
        squawkId.value,
        existing.copy(
          dismiss_reason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
          dismissed_at = null,
        ),
        scope,
      )
    }.onFailure { logger.w(it) { "Error reopening squawk ${squawkId.value}" } }

  companion object {
    private val logger = Logger.withTag("SquawkManager")
  }
}
