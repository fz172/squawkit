package dev.fanfly.wingslog.feature.export.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType.ATTACHMENT_TYPE_LINK
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryInfo
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryEmailSource
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
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
  private val taskDataManager: TaskDataManager,
  private val squawkManager: SquawkManager,
  private val auth: FirebaseAuth,
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

  private val today = clock.now()
    .toLocalDateTime(timeZone).date
  private val defaultConfiguring = ExportUiState.Configuring(
    customStart = today.minus(DatePeriod(months = 12)),
    customEnd = today,
  )

  private val _state = MutableStateFlow<ExportUiState>(defaultConfiguring)
  val state: StateFlow<ExportUiState> = _state.asStateFlow()

  private var lastConfiguring: ExportUiState.Configuring = defaultConfiguring
  private var exportJob: Job? = null
  private var hasInitializedSelection = false
  private var latestDeliveryInfo: ExportDeliveryInfo? = null

  init {
    observeAircraft()
    observeDeliveryInfo()
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
                combine(
                  logsManager.observeLogs(item.id),
                  taskDataManager.observeTasks(item.id),
                  squawkManager.observeSquawks(item.id),
                ) { logs, tasks, squawks ->
                  val attachmentSizeBytes =
                    logs.sumOf { it.attachments.exportedBytes() } +
                      tasks.sumOf { it.attachments.exportedBytes() } +
                      squawks.sumOf { it.attachments.exportedBytes() }
                  item.toSelectionRow(
                    logCount = logs.size,
                    attachmentSizeBytes = attachmentSizeBytes,
                  )
                }
              }
            ) { rows -> rows.toList() }
          }
        }
        .collect { rows ->
          _state.update { current ->
            val currentConfig =
              current as? ExportUiState.Configuring ?: lastConfiguring
            val rowIds = rows.map { it.aircraftId }
              .toSet()
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
            )
              .recomputeEstimates()
          }
            .also {
              (_state.value as? ExportUiState.Configuring)?.let {
                lastConfiguring = it
              }
            }
        }
    }
  }

  private fun observeDeliveryInfo() {
    viewModelScope.launch {
      auth.authStateChanged.collect { user ->
        val signedIn = user != null && !user.isAnonymous
        val authEmail = user?.email.orEmpty().trim()
        val info = when {
          !signedIn -> null
          authEmail.isNotBlank() -> ExportDeliveryInfo(authEmail, ExportDeliveryEmailSource.AUTH_FALLBACK)
          else -> null
        }
        latestDeliveryInfo = info
        val current = _state.value as? ExportUiState.Configuring ?: return@collect
        val next = current.copy(
          resolvedDeliveryInfo = info,
        )
        lastConfiguring = next
        _state.value = next
      }
    }
  }

  fun onToggleAircraft(id: String) = reduceConfiguring { current ->
    val selected = if (id in current.selectedAircraftIds) {
      current.selectedAircraftIds - id
    } else {
      current.selectedAircraftIds + id
    }
    current.copy(selectedAircraftIds = selected)
      .recomputeEstimates()
  }

  fun onSelectAll() = reduceConfiguring { current ->
    current.copy(
      selectedAircraftIds = current.aircraft.map { it.aircraftId }
        .toSet()
    )
      .recomputeEstimates()
  }

  fun onClearAll() = reduceConfiguring { current ->
    current.copy(selectedAircraftIds = emptySet())
      .recomputeEstimates()
  }

  /**
   * Toggles a report format, keeping at least one selected so the export always produces a document.
   */
  fun onToggleFormat(format: ExportFormat) = reduceConfiguring { current ->
    val next = if (format in current.formats) {
      if (current.formats.size == 1) return@reduceConfiguring current
      current.formats - format
    } else {
      current.formats + format
    }
    current.copy(formats = next)
      .recomputeEstimates()
  }

  fun onDateRangeChange(option: DateRangeOption) =
    reduceConfiguring { current ->
      current.copy(dateRange = option)
        .recomputeEstimates()
    }

  /**
   * Updates the inclusive custom start date and keeps the custom range valid.
   */
  fun onCustomStartChange(date: LocalDate) = reduceConfiguring { current ->
    val end = if (date > current.customEnd) date else current.customEnd
    current.copy(
      dateRange = DateRangeOption.Custom,
      customStart = date,
      customEnd = end,
    )
      .recomputeEstimates()
  }

  /**
   * Updates the inclusive custom end date and keeps the custom range valid.
   */
  fun onCustomEndChange(date: LocalDate) = reduceConfiguring { current ->
    val start = if (date < current.customStart) date else current.customStart
    current.copy(
      dateRange = DateRangeOption.Custom,
      customStart = start,
      customEnd = date,
    )
      .recomputeEstimates()
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
      exportManager.exportLogs(configuring.toRequest())
        .collect { progress ->
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
    includeOpenSquawks = true,
    formats = formats,
    destinationEmail = latestDeliveryInfo?.destinationEmail,
    destinationEmailSource = latestDeliveryInfo?.source?.name,
  )

  private fun ExportProgress.toUiState(): ExportUiState = when (this) {
    is ExportProgress.Running -> ExportUiState.Running(step, percent)
    is ExportProgress.Success -> ExportUiState.Success(
      fileName = fileName,
      displayLocation = displayLocation,
      displayLocationKind = displayLocationKind,
      filePath = filePath,
      sizeBytes = sizeBytes,
      formats = lastConfiguring.formats,
      selectedTailNumbers = lastConfiguring.aircraft
        .filter { it.aircraftId in lastConfiguring.selectedAircraftIds }
        .map { it.tailNumber },
      dateRange = lastConfiguring.dateRange,
      customStart = lastConfiguring.customStart,
      customEnd = lastConfiguring.customEnd,
      deliveryInfo = latestDeliveryInfo,
      deliveryState = deliveryState,
      deliveryFailureMessage = deliveryFailureMessage,
    )

    is ExportProgress.Error -> ExportUiState.Error(message)
  }

  private fun ExportUiState.Configuring.recomputeEstimates(): ExportUiState.Configuring {
    val selectedRows = aircraft.filter { it.aircraftId in selectedAircraftIds }
    val logCount = selectedRows.sumOf { it.logCount }
    val attachmentBytes = selectedRows.sumOf { it.attachmentSizeBytes }
    // Per-format contribution; attachments and README ride along regardless of selection.
    val reportBytes = formats.sumOf { format ->
      when (format) {
        ExportFormat.CSV -> 64_000L + (logCount * 600L) + (selectedRows.size * 8_000L)
        ExportFormat.PDF -> 28_000L + (logCount * 380L) + (selectedRows.size * 12_000L)
        ExportFormat.XLSX -> 40_000L + (logCount * 220L) + (selectedRows.size * 10_000L)
      }
    }
    return copy(
      estimatedLogCount = logCount,
      estimatedSizeBytes = if (selectedRows.isEmpty()) 0L else reportBytes + attachmentBytes,
    )
  }

  private fun Aircraft.toSelectionRow(
    logCount: Int,
    attachmentSizeBytes: Long,
  ) = AircraftSelectionRow(
    aircraftId = id,
    tailNumber = tail_number,
    makeModel = listOf(make, model).filter { it.isNotBlank() }
      .joinToString(" ")
      .ifBlank { serial },
    logCount = logCount,
    attachmentSizeBytes = attachmentSizeBytes,
  )

  private fun List<Attachment>.exportedBytes(): Long = filter { attachment ->
    attachment.type != ATTACHMENT_TYPE_LINK
  }.sumOf { attachment ->
    attachment.size_bytes.coerceAtLeast(0L)
  }
}
