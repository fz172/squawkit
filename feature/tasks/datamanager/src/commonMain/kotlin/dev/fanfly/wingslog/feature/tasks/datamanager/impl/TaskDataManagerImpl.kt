package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TaskDataManagerImpl(
  private val scopeResolver: AircraftScopeResolver,
  storeFactory: EntityStoreFactory,
) : TaskDataManager {

  private val store: EntityStore<MaintenanceTask> =
    storeFactory.create(CollectionKind.MaintenanceTask)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTasks(aircraftId: String): Flow<List<MaintenanceTask>> =
    scopeResolver.resolve(aircraftId).flatMapLatest { scope ->
      if (scope == null) {
        logger.d { "No signed-in user; stopping tasks observation for aircraft $aircraftId" }
        flowOf(emptyList())
      } else {
        store.observeAll(scope)
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Error observing tasks for aircraft $aircraftId" }
            emit(emptyList())
          }
      }
    }

  override suspend fun addTask(
    aircraftId: String,
    card: MaintenanceTask
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(aircraftId)
      val withId =
        if (card.id.isEmpty()) card.copy(id = generateRandomId()) else card
      store.put(withId.id, withId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding task" } }

  override suspend fun updateTask(
    aircraftId: String,
    card: MaintenanceTask
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(aircraftId)
      store.put(card.id, card, scope)
      true
    }.onFailure { logger.w(it) { "Error updating task ${card.id}" } }

  override suspend fun deleteTask(
    aircraftId: String,
    cardId: String
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(aircraftId)
      store.delete(cardId, scope)
      true
    }.onFailure { logger.w(it) { "Error deleting task $cardId" } }

  companion object {
    private val logger = Logger.withTag("TaskDataManagerImpl")
  }
}
