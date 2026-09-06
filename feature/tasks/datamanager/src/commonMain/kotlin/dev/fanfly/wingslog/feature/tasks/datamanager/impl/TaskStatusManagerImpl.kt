package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.currentReadings
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TaskStatusManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  storeFactory: EntityStoreFactory,
  private val dueManager: TaskDueManager,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : TaskStatusManager {

  private val taskStore: EntityStore<MaintenanceTask> = storeFactory.create(CollectionKind.MaintenanceTask)
  private val logStore: EntityStore<MaintenanceLog> = storeFactory.create(CollectionKind.MaintenanceLog)
  private val refreshTick = MutableStateFlow(0)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTasksWithStatus(thingId: String): Flow<List<MaintenanceTaskWithStatus>> =
    scopeResolver.resolve(thingId).flatMapLatest { scope ->
      if (scope == null) return@flatMapLatest flowOf(emptyList())
      // One table backs every collection, so each store re-emits on any write; distinctUntilChanged
      // keeps the due computation to real changes.
      combine(
        taskStore.observeAll(scope).map { rows -> rows.map { it.value } }.distinctUntilChanged(),
        logStore.observeAll(scope).map { rows -> rows.map { it.value } }.distinctUntilChanged(),
        refreshTick,
      ) { tasks, logs, _ -> withStatus(tasks, logs) }
        .catch { e ->
          logger.w(e) { "Error observing task status for thing $thingId" }
          emit(emptyList())
        }
    }

  override fun refreshDueStatus() {
    refreshTick.value++
  }

  private fun withStatus(tasks: List<MaintenanceTask>, logs: List<MaintenanceLog>): List<MaintenanceTaskWithStatus> {
    val withStatus = tasks.map { MaintenanceTaskWithStatus(it, dueManager.computeNextDue(it, logs, tasks)) }
    val readings = currentReadings(logs).associate { it.meter_key to it.value_ }
    val today = clock.now().toLocalDateTime(timeZone).date
    val active = withStatus
      .filter { it.dueStatus.status != DueStatus.COMPLIED }
      .sortedBy { task ->
        val due = task.dueStatus
        if (due.isImmediate) return@sortedBy Long.MIN_VALUE
        val candidates = mutableListOf<Long>()
        due.nextDueDate?.let { candidates.add(it.toEpochDays() - today.toEpochDays()) }
        due.nextDueEngine?.let {
          // Remaining in the due’s own meter; raw values would sort mileage tasks behind hours (#759).
          val current = readings[due.nextDueMeterKey.orEmpty()] ?: 0.0
          candidates.add((it.toDouble() - current).toLong())
        }
        candidates.minOrNull() ?: Long.MAX_VALUE
      }
    return active + withStatus.filter { it.dueStatus.status == DueStatus.COMPLIED }
  }

  private companion object {
    val logger = Logger.withTag("TaskStatusManagerImpl")
  }
}
