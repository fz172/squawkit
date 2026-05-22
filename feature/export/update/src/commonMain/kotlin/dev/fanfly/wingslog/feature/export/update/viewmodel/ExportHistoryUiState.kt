package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.export.ExportRecord

/**
 * State model for the export history destination.
 */
sealed interface ExportHistoryUiState {
  data object Loading : ExportHistoryUiState

  /**
   * @param canEmailDelivery true when the signed-in user has an email account, so sharing an export
   * sends a fresh delivery email instead of opening the device share sheet.
   */
  data class Loaded(
    val exports: List<ExportRecord>,
    val canEmailDelivery: Boolean = false,
  ) : ExportHistoryUiState
}
