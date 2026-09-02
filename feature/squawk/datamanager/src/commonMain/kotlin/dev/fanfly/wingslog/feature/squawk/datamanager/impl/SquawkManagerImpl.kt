package dev.fanfly.wingslog.feature.squawk.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class SquawkManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  storeFactory: EntityStoreFactory,
) : SquawkManager {

  private val store: EntityStore<Squawk> =
    storeFactory.create(CollectionKind.Squawk)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeSquawks(thingId: String): Flow<List<Squawk>> =
    scopeResolver.resolve(thingId).flatMapLatest { scope ->
      if (scope == null) {
        flowOf(emptyList())
      } else {
        store.observeAll(scope)
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Error observing squawks for thing $thingId" }
            emit(emptyList())
          }
      }
    }

  override suspend fun addSquawk(
    thingId: String,
    squawk: Squawk
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val withId =
        if (squawk.id.isEmpty()) squawk.copy(id = generateRandomId()) else squawk
      store.put(withId.id, withId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding squawk" } }

  override suspend fun updateSquawk(
    thingId: String,
    squawk: Squawk
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      store.put(squawk.id, squawk, scope)
      true
    }.onFailure { logger.w(it) { "Error updating squawk ${squawk.id}" } }

  override suspend fun deleteSquawk(
    thingId: String,
    squawkId: String
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      store.delete(squawkId, scope)
      true
    }.onFailure { logger.w(it) { "Error deleting squawk $squawkId" } }

  override suspend fun markAddressed(
    thingId: String,
    squawkIds: List<String>,
    logId: String,
  ): Result<Unit> = runCatching {
    val scope = scopeResolver.resolveNow(thingId)
    val allSquawks = store.observeAll(scope)
      .firstOrNull()
      ?.associateBy { it.id } ?: emptyMap()
    squawkIds.forEach { squawkId ->
      val existing = allSquawks[squawkId]?.value ?: return@forEach
      store.put(squawkId, existing.copy(addressed_by_log_id = logId), scope)
    }
  }.onFailure { logger.w(it) { "Error marking squawks addressed: $squawkIds" } }

  override suspend fun dismissSquawk(
    thingId: String,
    squawkId: String,
    reason: SquawkDismissReason,
  ): Result<Unit> = runCatching {
    val scope = scopeResolver.resolveNow(thingId)
    val existing = store.observeAll(scope)
      .firstOrNull()
      ?.associateBy { it.id }
      ?.get(squawkId)?.value
      ?: error("Squawk $squawkId not found")
    store.put(
      squawkId,
      existing.copy(
        dismiss_reason = reason,
        dismissed_at = Clock.System.now()
          .toWireInstant(),
      ),
      scope,
    )
  }.onFailure { logger.w(it) { "Error dismissing squawk $squawkId" } }

  override suspend fun reopenSquawk(
    thingId: String,
    squawkId: String
  ): Result<Unit> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val existing = store.observeAll(scope)
        .firstOrNull()
        ?.associateBy { it.id }
        ?.get(squawkId)?.value
        ?: error("Squawk $squawkId not found")
      store.put(
        squawkId,
        existing.copy(
          dismiss_reason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
          dismissed_at = null,
        ),
        scope,
      )
    }.onFailure { logger.w(it) { "Error reopening squawk $squawkId" } }

  companion object {
    private val logger = Logger.withTag("SquawkManager")
  }
}
