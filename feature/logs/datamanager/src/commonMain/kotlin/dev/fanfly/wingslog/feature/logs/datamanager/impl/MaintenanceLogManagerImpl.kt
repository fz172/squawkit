package dev.fanfly.wingslog.feature.logs.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.currentReadings
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MeterReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MaintenanceLogManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  storeFactory: EntityStoreFactory,
) : MaintenanceLogManager {

  private val logStore: EntityStore<MaintenanceLog> =
    storeFactory.create(CollectionKind.MaintenanceLog)
  private val overviewStore: EntityStore<MaintenanceOverview> =
    storeFactory.create(CollectionKind.MaintenanceOverview)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLogAuthors(thingId: String): Flow<Map<String, String?>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) flowOf(emptyMap())
        else logStore.observeAll(scope)
          .map { rows -> rows.associate { it.id to it.writerUid } }
          .catch { e ->
            logger.w(e) { "Error observing log authorship for thing $thingId" }
            emit(emptyMap())
          }
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLogs(thingId: String): Flow<List<MaintenanceLog>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          logger.d { "No signed-in user; stopping logs observation for thing $thingId" }
          flowOf(emptyList())
        } else {
          logStore.observeAll(scope)
            .map { rows ->
              rows.map { it.value }
                .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
            }
            .catch { e ->
              logger.w(e) { "Error observing logs for thing $thingId" }
              emit(emptyList())
            }
        }
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeMaintenanceOverview(thingId: String): Flow<MaintenanceOverview?> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          flowOf(null)
        } else {
          overviewStore.observe(OVERVIEW_ID, scope)
            .map { it?.value }
            .catch { e ->
              logger.w(e) { "Error observing overview for thing $thingId" }
              emit(null)
            }
        }
      }

  override suspend fun addLog(
    thingId: String,
    log: MaintenanceLog
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val withId =
        if (log.id.isEmpty()) log.copy(id = generateRandomId()) else log
      logStore.put(withId.id, withId, scope)
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding log" } }

  override suspend fun updateLog(
    thingId: String,
    log: MaintenanceLog
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      logStore.put(log.id, log, scope)
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error updating log ${log.id}" } }

  override suspend fun deleteLog(
    thingId: String,
    logId: String
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      logStore.delete(logId, scope)
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error deleting log $logId" } }

  // Overview is recomputed from the logs after every mutation. With local SQLite this is cheap,
  // and keeping the doc on disk lets observers read it without holding a logs-flow subscription.
  private suspend fun refreshOverview(thingId: String, scope: EntityScope) {
    val logs = logStore.observeAll(scope)
      .first()
      .map { it.value }
    val current = currentReadings(logs)
    val overview = MaintenanceOverview(
      aircraft_id = thingId,
      total_log_count = logs.size,
      airframe_log_count = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME },
      engine_log_count = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE },
      propeller_log_count = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER },
      // The maximum this Thing's logs record for each meter, whatever meters those are — which is
      // what a car's odometer needed and a fixed set of aviation doubles could never hold (#730).
      current = current,
    )
    overviewStore.put(OVERVIEW_ID, overview, scope)
  }

  companion object {
    private val logger = Logger.withTag("MaintenanceLogManagerImpl")

    // Single fixed id for the overview doc; the scope already includes the thing id so this
    // constant doesn't need to vary per thing.
    private const val OVERVIEW_ID = "main"
  }
}

/** The value for [meterKey], or 0.0 — the legacy overview fields have no absent state. */
private fun List<MeterReading>.valueFor(meterKey: String): Double =
  firstOrNull { it.meter_key == meterKey }?.value_ ?: 0.0
