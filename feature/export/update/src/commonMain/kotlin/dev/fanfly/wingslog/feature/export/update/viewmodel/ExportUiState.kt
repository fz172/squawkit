package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import kotlinx.datetime.LocalDate

sealed interface ExportUiState {
  data class Configuring(
    val aircraft: List<AircraftSelectionRow> = emptyList(),
    val selectedAircraftIds: Set<String> = emptySet(),
    val dateRange: DateRangeOption = DateRangeOption.AllTime,
    val customStart: LocalDate,
    val customEnd: LocalDate,
    val includeOpenSquawks: Boolean = true,
    val estimatedSizeBytes: Long = 0L,
    val estimatedLogCount: Int = 0,
    val isLoadingAircraft: Boolean = true,
  ) : ExportUiState

  data class Running(val step: String, val percent: Int) : ExportUiState

  data class Success(
    val fileName: String,
    val displayLocation: String,
    val displayLocationKind: ExportDisplayLocation,
    val filePath: String,
    val sizeBytes: Long,
  ) : ExportUiState

  data class Error(val message: String) : ExportUiState
}

data class AircraftSelectionRow(
  val aircraftId: String,
  val tailNumber: String,
  val makeModel: String,
  val logCount: Int,
)

enum class DateRangeOption {
  AllTime,
  Last12Months,
  Custom,
}
