package dev.fanfly.wingslog.feature.export.update.viewmodel

import dev.fanfly.wingslog.export.ExportRecord

/**
 * State model for the export history destination.
 */
sealed interface ExportHistoryUiState {
  data object Loading : ExportHistoryUiState

  data class Loaded(val exports: List<ExportRecord>) : ExportHistoryUiState
}
