package dev.fanfly.wingslog.feature.logs.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MaintenanceLogManagerImpl(
  private val scopeResolver: AircraftScopeResolver,
  storeFactory: EntityStoreFactory,
) : MaintenanceLogManager {

  private val logStore: EntityStore<MaintenanceLog> =
    storeFactory.create(CollectionKind.MaintenanceLog)
  private val overviewStore: EntityStore<MaintenanceOverview> =
    storeFactory.create(CollectionKind.MaintenanceOverview)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLogAuthors(aircraftId: String): Flow<Map<String, String?>> =
    scopeResolver.resolve(aircraftId).flatMapLatest { scope ->
      if (scope == null) flowOf(emptyMap())
      else logStore.observeAll(scope)
        .map { rows -> rows.associate { it.id to it.writerUid } }
        .catch { e ->
          logger.w(e) { "Error observing log authorship for aircraft $aircraftId" }
          emit(emptyMap())
        }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLogs(aircraftId: String): Flow<List<MaintenanceLog>> =
    scopeResolver.resolve(aircraftId).flatMapLatest { scope ->
      if (scope == null) {
        logger.d { "No signed-in user; stopping logs observation for aircraft $aircraftId" }
        flowOf(emptyList())
      } else {
        logStore.observeAll(scope)
          .map { rows ->
            rows.map { it.value }
              .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
          }
          .catch { e ->
            logger.w(e) { "Error observing logs for aircraft $aircraftId" }
            emit(emptyList())
          }
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeMaintenanceOverview(aircraftId: String): Flow<MaintenanceOverview?> =
    scopeResolver.resolve(aircraftId).flatMapLatest { scope ->
      if (scope == null) {
        flowOf(null)
      } else {
        overviewStore.observe(OVERVIEW_ID, scope)
          .map { it?.value }
          .catch { e ->
            logger.w(e) { "Error observing overview for aircraft $aircraftId" }
            emit(null)
          }
      }
    }

  override suspend fun addLog(
    aircraftId: String,
    log: MaintenanceLog
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(aircraftId)
      val withId =
        if (log.id.isEmpty()) log.copy(id = generateRandomId()) else log
      logStore.put(withId.id, withId, scope)
      refreshOverview(aircraftId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding log" } }

  override suspend fun updateLog(
    aircraftId: String,
    log: MaintenanceLog
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(aircraftId)
      logStore.put(log.id, log, scope)
      refreshOverview(aircraftId, scope)
      true
    }.onFailure { logger.w(it) { "Error updating log ${log.id}" } }

  override suspend fun deleteLog(
    aircraftId: String,
    logId: String
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(aircraftId)
      logStore.delete(logId, scope)
      refreshOverview(aircraftId, scope)
      true
    }.onFailure { logger.w(it) { "Error deleting log $logId" } }

  // Overview is recomputed from the logs after every mutation. With local SQLite this is cheap,
  // and keeping the doc on disk lets observers read it without holding a logs-flow subscription.
  private suspend fun refreshOverview(aircraftId: String, scope: EntityScope) {
    val logs = logStore.observeAll(scope)
      .first()
      .map { it.value }
    val overview = MaintenanceOverview(
      aircraft_id = aircraftId,
      total_log_count = logs.size,
      airframe_log_count = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME },
      engine_log_count = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE },
      propeller_log_count = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER },
      current_airframe_time =
        logs.filter { it.airframe_time > 0.0 }
          .maxOfOrNull { it.airframe_time } ?: 0.0,
      current_engine_time =
        logs.filter { it.engine_hour > 0.0 }
          .maxOfOrNull { it.engine_hour } ?: 0.0,
      current_propeller_time =
        logs.filter { it.prop_time > 0.0 }
          .maxOfOrNull { it.prop_time } ?: 0.0,
    )
    overviewStore.put(OVERVIEW_ID, overview, scope)
  }

  companion object {
    private val logger = Logger.withTag("MaintenanceLogManagerImpl")

    // Single fixed id for the overview doc; the scope already includes the aircraft id so this
    // constant doesn't need to vary per aircraft.
    private const val OVERVIEW_ID = "main"
  }
}
