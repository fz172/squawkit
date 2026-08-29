package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryInfo
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import kotlinx.datetime.LocalDate

/**
 * Complete state model for the export selection destination.
 */
sealed interface ExportUiState {
  /**
   * Editable export setup with live thing selection and size estimates.
   */
  data class Configuring(
    val thing: List<AircraftSelectionRow> = emptyList(),
    val selectedThingIds: Set<String> = emptySet(),
    val formats: Set<ExportFormat> = ExportFormat.ALL,
    val dateRange: DateRangeOption = DateRangeOption.AllTime,
    val customStart: LocalDate,
    val customEnd: LocalDate,
    val resolvedDeliveryInfo: ExportDeliveryInfo? = null,
    // True when the user would qualify for email delivery (signed in with an email) but the Pro
    // gate is off. The email option is surfaced shown-locked as a promo rather than hidden; local
    // (save-to-device) export is unaffected. Mutually exclusive with a non-null resolvedDeliveryInfo.
    val emailDeliveryLocked: Boolean = false,
    val estimatedSizeBytes: Long = 0L,
    val estimatedLogCount: Int = 0,
    val isLoadingAircraft: Boolean = true,
  ) : ExportUiState

  data class Running(val step: ExportProgressStep, val percent: Int) :
    ExportUiState

  /**
   * Completed export details shown on the result screen after the archive is saved.
   *
   * Delivery is never automatic: [deliveryInfo] non-null just means the signed-in user is
   * Pro-eligible for email delivery, and [persistedDeliveryState]/[deliveryFailureMessage] only
   * change once the user explicitly taps "Send to my email" on this screen.
   */
  data class Success(
    val exportId: String,
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
    // Shown-locked promo for the "Send to my email" action when the user is eligible but not Pro.
    val emailDeliveryLocked: Boolean = false,
    val persistedDeliveryState: String = "",
    val deliveryFailureMessage: String = "",
    val isSendingEmail: Boolean = false,
  ) : ExportUiState

  data class Error(val message: String) : ExportUiState
}
