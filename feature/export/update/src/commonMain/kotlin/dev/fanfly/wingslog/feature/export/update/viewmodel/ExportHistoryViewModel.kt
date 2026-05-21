package dev.fanfly.wingslog.feature.export.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads previously generated export archives and supports removing them.
 */
class ExportHistoryViewModel(
  private val exportManager: ExportManager,
) : ViewModel() {

  private val _state = MutableStateFlow<ExportHistoryUiState>(ExportHistoryUiState.Loading)
  val state: StateFlow<ExportHistoryUiState> = _state.asStateFlow()

  init {
    refresh()
  }

  /**
   * Reloads the export list from disk.
   */
  fun refresh() {
    viewModelScope.launch {
      _state.value = ExportHistoryUiState.Loaded(exportManager.listExports())
    }
  }

  /**
   * Deletes the export at [exportId] and reloads the list.
   */
  fun onDelete(exportId: String) {
    viewModelScope.launch {
      exportManager.deleteExport(exportId)
      _state.value = ExportHistoryUiState.Loaded(exportManager.listExports())
    }
  }
}
