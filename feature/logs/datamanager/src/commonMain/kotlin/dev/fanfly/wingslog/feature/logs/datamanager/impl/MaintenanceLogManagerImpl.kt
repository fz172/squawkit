package dev.fanfly.wingslog.feature.logs.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.CurrentReading
import dev.fanfly.wingslog.core.template.currentReadingStates
import dev.fanfly.wingslog.core.template.currentReadings
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.ManualMeterReading
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Squawk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class MaintenanceLogManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  storeFactory: EntityStoreFactory,
  private val clock: Clock = Clock.System,
) : MaintenanceLogManager {

  private val logStore: EntityStore<MaintenanceLog> =
    storeFactory.create(CollectionKind.MaintenanceLog)
  private val overviewStore: EntityStore<MaintenanceOverview> =
    storeFactory.create(CollectionKind.MaintenanceOverview)

  // One row per meter, keyed by the meter’s key, so setting a reading overwrites the last one.
  private val manualStore: EntityStore<ManualMeterReading> =
    storeFactory.create(CollectionKind.ManualReading)

  // Squawks are read here only to reopen the ones a deleted log addressed; see [reopenAddressed].
  private val squawkStore: EntityStore<Squawk> =
    storeFactory.create(CollectionKind.Squawk)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLogAuthors(thingId: ThingId): Flow<Map<MaintenanceLogId, String?>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) flowOf(emptyMap())
        else logStore.observeAll(scope)
          .map { rows -> rows.associate { MaintenanceLogId(it.id) to it.writerUid } }
          .catch { e ->
            logger.w(e) { "Error observing log authorship for thing ${thingId.value}" }
            emit(emptyMap())
          }
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLogs(thingId: ThingId): Flow<List<MaintenanceLog>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          logger.d { "No signed-in user; stopping logs observation for thing ${thingId.value}" }
          flowOf(emptyList())
        } else {
          logStore.observeAll(scope)
            .map { rows ->
              rows.map { it.value }
                .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
            }
            .catch { e ->
              logger.w(e) { "Error observing logs for thing ${thingId.value}" }
              emit(emptyList())
            }
        }
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeMaintenanceOverview(thingId: ThingId): Flow<MaintenanceOverview?> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) {
          flowOf(null)
        } else {
          overviewStore.observe(OVERVIEW_ID, scope)
            .map { it?.value }
            .catch { e ->
              logger.w(e) { "Error observing overview for thing ${thingId.value}" }
              emit(null)
            }
        }
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeManualReadings(thingId: ThingId): Flow<List<ManualMeterReading>> =
    scopeResolver.resolve(thingId)
      .flatMapLatest { scope ->
        if (scope == null) flowOf(emptyMap<String, Any?>().let { emptyList() })
        else manualStore.observeAll(scope)
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Error observing manual readings for thing ${thingId.value}" }
            emit(emptyList())
          }
      }

  override fun observeCurrentReadings(thingId: ThingId): Flow<List<CurrentReading>> =
    combine(
      observeLogs(thingId),
      observeManualReadings(thingId),
    ) { logs, manual -> currentReadingStates(logs, manual) }
      .distinctUntilChanged()

  override suspend fun setManualReading(
    thingId: ThingId,
    meterKey: String,
    value: Double,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      manualStore.put(
        meterKey,
        ManualMeterReading(
          reading = MeterReading(meter_key = meterKey, value_ = value),
          set_at = clock.now()
            .toWireInstant(),
        ),
        scope
      )
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error setting reading for $meterKey" } }

  override suspend fun addLog(
    thingId: ThingId,
    log: MaintenanceLog,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val withId =
        if (log.id.isEmpty()) log.copy(id = generateRandomId()) else log
      logStore.put(withId.id, withId.withReadingsSavedNow(), scope)
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error adding log" } }

  override suspend fun updateLog(
    thingId: ThingId,
    log: MaintenanceLog,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      val stored = logStore.observe(log.id, scope)
        .first()?.value
      // The stored stamp, not the incoming one: the form rebuilds the log from its fields and does
      // not carry the stamp through an edit.
      val stamped =
        if (stored != null && stored.sameReadingsAs(log)) {
          log.copy(readings_saved_at = stored.readings_saved_at)
        } else log.withReadingsSavedNow()
      logStore.put(log.id, stamped, scope)
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error updating log ${log.id}" } }

  // A log that records no meter has nothing to order, so it carries no stamp.
  private fun MaintenanceLog.withReadingsSavedNow(): MaintenanceLog =
    copy(
      readings_saved_at = if (readings.isEmpty()) null else clock.now()
        .toWireInstant()
    )

  // Order is the form’s, not the user’s: the same values listed differently are the same readings.
  private fun MaintenanceLog.sameReadingsAs(other: MaintenanceLog): Boolean =
    readings.toSet() == other.readings.toSet()

  override suspend fun deleteLog(
    thingId: ThingId,
    logId: MaintenanceLogId,
  ): Result<Boolean> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId)
      logStore.delete(logId.value, scope)
      reopenAddressed(logId, scope)
      refreshOverview(thingId, scope)
      true
    }.onFailure { logger.w(it) { "Error deleting log ${logId.value}" } }

  // The fix was un-logged, so the squawks it closed are open again (#815). Matched on the stored
  // `addressed_by_log_id` rather than the log's `squawk_ids`, which drifts when a log is edited.
  private suspend fun reopenAddressed(logId: MaintenanceLogId, scope: EntityScope) {
    squawkStore.observeAll(scope)
      .first()
      .filter { it.value.addressed_by_log_id == logId.value }
      .forEach {
        squawkStore.put(
          it.id,
          it.value.copy(addressed_by_log_id = ""),
          scope
        )
      }
  }

  // Overview is recomputed from the logs after every mutation. With local SQLite this is cheap,
  // and keeping the doc on disk lets observers read it without holding a logs-flow subscription.
  private suspend fun refreshOverview(thingId: ThingId, scope: EntityScope) {
    val logs = logStore.observeAll(scope)
      .first()
      .map { it.value }
    val manual = manualStore.observeAll(scope)
      .first()
      .map { it.value }
    val current = currentReadings(logs, manual)
    val overview = MaintenanceOverview(
      aircraft_id = thingId.value,
      total_log_count = logs.size,
      airframe_log_count = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME },
      engine_log_count = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE },
      propeller_log_count = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER },
      // The most recent reading of each meter, from a log or set by hand, whatever meters those
      // are — which is what a car's odometer needed and a fixed set of aviation doubles could never
      // hold (#730).
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
