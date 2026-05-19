package dev.fanfly.wingslog.feature.export.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Coordinates export selection state and progress for the export destination.
 */
class ExportViewModel(
  private val exportManager: ExportManager,
  private val fleetManager: FleetManager,
  private val logsManager: MaintenanceLogManager,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

  private val today = clock.now().toLocalDateTime(timeZone).date
  private val defaultConfiguring = ExportUiState.Configuring(
    customStart = today.minus(DatePeriod(months = 12)),
    customEnd = today,
  )

  private val _state = MutableStateFlow<ExportUiState>(defaultConfiguring)
  val state: StateFlow<ExportUiState> = _state.asStateFlow()

  private var lastConfiguring: ExportUiState.Configuring = defaultConfiguring
  private var exportJob: Job? = null
  private var hasInitializedSelection = false

  init {
    observeAircraft()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeAircraft() {
    viewModelScope.launch {
      fleetManager.observeFleetDashboard()
        .flatMapLatest { aircraft ->
          if (aircraft.isEmpty()) {
            flowOf(emptyList())
          } else {
            combine(
              aircraft.map { item ->
                logsManager.observeLogs(item.id).map { logs ->
                  item.toSelectionRow(logCount = logs.size)
                }
              }
            ) { rows -> rows.toList() }
          }
        }
        .collect { rows ->
          _state.update { current ->
            val currentConfig = current as? ExportUiState.Configuring ?: lastConfiguring
            val rowIds = rows.map { it.aircraftId }.toSet()
            val selectedIds = if (!hasInitializedSelection) {
              hasInitializedSelection = true
              rowIds
            } else {
              currentConfig.selectedAircraftIds.intersect(rowIds)
            }
            currentConfig.copy(
              aircraft = rows,
              selectedAircraftIds = selectedIds,
              isLoadingAircraft = false,
            ).recomputeEstimates()
          }.also {
            (_state.value as? ExportUiState.Configuring)?.let { lastConfiguring = it }
          }
        }
    }
  }

  fun onToggleAircraft(id: String) = reduceConfiguring { current ->
    val selected = if (id in current.selectedAircraftIds) {
      current.selectedAircraftIds - id
    } else {
      current.selectedAircraftIds + id
    }
    current.copy(selectedAircraftIds = selected).recomputeEstimates()
  }

  fun onSelectAll() = reduceConfiguring { current ->
    current.copy(
      selectedAircraftIds = current.aircraft.map { it.aircraftId }.toSet()
    ).recomputeEstimates()
  }

  fun onClearAll() = reduceConfiguring { current ->
    current.copy(selectedAircraftIds = emptySet()).recomputeEstimates()
  }

  fun onDateRangeChange(option: DateRangeOption) = reduceConfiguring { current ->
    current.copy(dateRange = option).recomputeEstimates()
  }

  fun onToggleIncludeOpenSquawks() = reduceConfiguring { current ->
    current.copy(includeOpenSquawks = !current.includeOpenSquawks).recomputeEstimates()
  }

  /**
   * Starts export generation using the current configuration.
   */
  fun onExport() {
    val configuring = _state.value as? ExportUiState.Configuring ?: return
    if (configuring.selectedAircraftIds.isEmpty()) return
    lastConfiguring = configuring
    exportJob?.cancel()
    exportJob = viewModelScope.launch {
      exportManager.exportLogs(configuring.toRequest()).collect { progress ->
        _state.value = progress.toUiState()
      }
    }
  }

  /**
   * Cancels an in-flight export and restores the last editable configuration.
   */
  fun onCancel() {
    exportJob?.cancel()
    exportJob = null
    _state.value = lastConfiguring
  }

  /**
   * Dismisses terminal export state without discarding the previous configuration.
   */
  fun onDone() {
    exportJob = null
    _state.value = lastConfiguring
  }

  /**
   * Returns from an error state to the last editable configuration.
   */
  fun onRetry() {
    _state.value = lastConfiguring
  }

  private fun reduceConfiguring(
    transform: (ExportUiState.Configuring) -> ExportUiState.Configuring,
  ) {
    val current = _state.value as? ExportUiState.Configuring ?: return
    val next = transform(current)
    lastConfiguring = next
    _state.value = next
  }

  private fun ExportUiState.Configuring.toRequest() = ExportRequest(
    aircraftIds = selectedAircraftIds.toList(),
    dateRange = when (dateRange) {
      DateRangeOption.AllTime -> ExportDateRange.AllTime
      DateRangeOption.Last12Months -> ExportDateRange.LastNMonths(12)
      DateRangeOption.Custom -> ExportDateRange.Custom(customStart, customEnd)
    },
    includeOpenSquawks = includeOpenSquawks,
  )

  private fun ExportProgress.toUiState(): ExportUiState = when (this) {
    is ExportProgress.Running -> ExportUiState.Running(step, percent)
    is ExportProgress.Success -> ExportUiState.Success(
      fileName = filePath.substringAfterLast('/').ifBlank { filePath },
      displayLocation = displayLocation,
      displayLocationKind = displayLocationKind,
      filePath = filePath,
      sizeBytes = sizeBytes,
    )
    is ExportProgress.Error -> ExportUiState.Error(message)
  }

  private fun ExportUiState.Configuring.recomputeEstimates(): ExportUiState.Configuring {
    val selectedRows = aircraft.filter { it.aircraftId in selectedAircraftIds }
    val logCount = selectedRows.sumOf { it.logCount }
    val csvBytes = 64_000L + (logCount * 600L) + (selectedRows.size * 8_000L)
    return copy(
      estimatedLogCount = logCount,
      estimatedSizeBytes = if (selectedRows.isEmpty()) 0L else csvBytes,
    )
  }

  private fun Aircraft.toSelectionRow(logCount: Int) = AircraftSelectionRow(
    aircraftId = id,
    tailNumber = tail_number,
    makeModel = listOf(make, model).filter { it.isNotBlank() }.joinToString(" ")
      .ifBlank { serial },
    logCount = logCount,
  )
}
