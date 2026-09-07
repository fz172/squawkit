package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.datamanager.withForcedDueMeter
import dev.fanfly.wingslog.thing.ForceCompliedStatus
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class TaskDataManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  private val commentManager: CommentManager,
  storeFactory: EntityStoreFactory,
) : TaskDataManager {

  private val store: EntityStore<MaintenanceTask> =
    storeFactory.create(CollectionKind.MaintenanceTask)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTasks(thingId: String): Flow<List<MaintenanceTask>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          logger.d { "No signed-in user; stopping tasks observation for thing $thingId" }
          flowOf(emptyList())
        } else {
          store.observeAll(scope)
            .map { rows -> rows.map { it.value } }
            .catch { e ->
              logger.w(e) { "Error observing tasks for thing $thingId" }
              emit(emptyList())
            }
        }
      }

  override suspend fun addTask(
    thingId: String,
    card: MaintenanceTask
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val withId =
        if (card.id.isEmpty()) card.copy(id = generateRandomId()) else card
      store.put(withId.id, withId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding task" } }

  override suspend fun updateTask(
    thingId: String,
    card: MaintenanceTask
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      store.put(card.id, card, scope)
      true
    }.onFailure { logger.w(it) { "Error updating task ${card.id}" } }

  override suspend fun skipCycle(
    thingId: String,
    card: MaintenanceTask,
    currentReading: Float,
  ): Result<Boolean> {
    val skipped = card.withForcedDueMeter(card.defaultMeterKey(), null)
      .copy(
        force_due_date = null,
        force_complied_status = ForceCompliedStatus(
          complied_date = toWireInstant(Clock.System.now().epochSeconds),
          // Keyed, so a complied status records which meter it was measured in.
          complied_meter = MeterReading(
            meter_key = card.defaultMeterKey(),
            value_ = currentReading.toDouble(),
          ),
        )
      )
    return updateTask(thingId, skipped)
  }

  override suspend fun deleteTask(
    thingId: String,
    cardId: String
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      store.delete(cardId, scope)
      // The thread goes with its record; see CommentManager.deleteThread.
      commentManager.deleteThread(
        CommentTarget(thingId, cardId, CommentParentKind.MAINTENANCE_TASK)
      )
      true
    }.onFailure { logger.w(it) { "Error deleting task $cardId" } }

  companion object {
    private val logger = Logger.withTag("TaskDataManagerImpl")
  }
}
