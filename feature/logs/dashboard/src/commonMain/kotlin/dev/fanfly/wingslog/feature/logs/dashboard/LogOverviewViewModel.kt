package dev.fanfly.wingslog.feature.logs.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.Thing
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The Overview tab's log slice: computes [LogStats] for the meters declared by the Thing's
 * template, the 4 most recent logs, and handles manual meter-reading updates.
 */
class LogOverviewViewModel(
  private val logManager: MaintenanceLogManager,
  private val templateRegistry: TemplateRegistry,
  private val thing: Thing,
) : ViewModel() {

  private val _events = Channel<UiText>(Channel.BUFFERED)
  val events: Flow<UiText> = _events.receiveAsFlow()

  val uiState: StateFlow<LogOverviewUiState> = combine(
    logManager.observeLogs(thing.id)
      .distinctUntilChanged()
      .catch { emit(emptyList()) },
    logManager.observeCurrentReadings(thing.id)
      .distinctUntilChanged()
      .catch { emit(emptyList()) },
  ) { logs, currentReadings ->
    val template = templateRegistry.forThingWithFallback(thing)
    val declared = currentReadings.filter { reading ->
      template.meters.any { it.key == reading.meterKey }
    }
    val stats = LogStats(
      total = logs.size.toLong(),
      airframe = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME }.toLong(),
      engine = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE }.toLong(),
      propeller = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER }.toLong(),
      readings = declared.associate { it.meterKey to it.value },
      readingsAsOf = declared.mapNotNull { it.asOf }.maxOrNull(),
    )
    LogOverviewUiState(
      loaded = true,
      logStats = stats,
      recentLogs = logs
        .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
        .take(4),
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, LogOverviewUiState())

  fun setMeterReading(meterKey: String, value: Double) {
    viewModelScope.launch {
      // Nothing to do on success: the card redraws from the reading the manager just stored.
      logManager.setManualReading(thing.id, meterKey, value)
        .onFailure {
          _events.send(UiText.StringRes(CoreRes.string.save_failed))
        }
    }
  }
}
