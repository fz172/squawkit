package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryInfo
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import kotlinx.datetime.LocalDate

/**
 * Complete state model for the export selection destination.
 */
sealed interface ExportUiState {
  /**
   * Editable export setup with live aircraft selection and size estimates.
   */
  data class Configuring(
    val aircraft: List<AircraftSelectionRow> = emptyList(),
    val selectedAircraftIds: Set<String> = emptySet(),
    val formats: Set<ExportFormat> = ExportFormat.ALL,
    val dateRange: DateRangeOption = DateRangeOption.AllTime,
    val customStart: LocalDate,
    val customEnd: LocalDate,
    val resolvedDeliveryInfo: ExportDeliveryInfo? = null,
    val estimatedSizeBytes: Long = 0L,
    val estimatedLogCount: Int = 0,
    val isLoadingAircraft: Boolean = true,
  ) : ExportUiState

  data class Running(val step: ExportProgressStep, val percent: Int) :
    ExportUiState

  /**
   * Completed export details shown on the result screen after the archive is saved.
   */
  data class Success(
    val fileName: String,
    val displayLocation: String,
    val displayLocationKind: ExportDisplayLocation,
    val filePath: String,
    val sizeBytes: Long,
    val formats: Set<ExportFormat>,
    val selectedTailNumbers: List<String>,
    val dateRange: DateRangeOption,
    val customStart: LocalDate,
    val customEnd: LocalDate,
    val deliveryInfo: ExportDeliveryInfo?,
    val deliveryState: String = "",
    val deliveryFailureMessage: String = "",
  ) : ExportUiState

  data class Error(val message: String) : ExportUiState
}
