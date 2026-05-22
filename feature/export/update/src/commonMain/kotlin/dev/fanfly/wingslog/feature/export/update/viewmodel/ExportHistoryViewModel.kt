package dev.fanfly.wingslog.feature.export.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads previously generated export archives and supports removing them.
 */
class ExportHistoryViewModel(
  private val exportManager: ExportManager,
  private val auth: FirebaseAuth,
) : ViewModel() {

  private val _state = MutableStateFlow<ExportHistoryUiState>(ExportHistoryUiState.Loading)
  val state: StateFlow<ExportHistoryUiState> = _state.asStateFlow()

  // Email-account users share by re-sending the delivery email; everyone else uses the device sheet.
  private var canEmailDelivery: Boolean = false

  init {
    observeAuth()
    refresh()
  }

  private fun observeAuth() {
    viewModelScope.launch {
      auth.authStateChanged.collect { user ->
        canEmailDelivery =
          user != null && !user.isAnonymous && !user.email.isNullOrBlank()
        _state.update { current ->
          if (current is ExportHistoryUiState.Loaded) {
            current.copy(canEmailDelivery = canEmailDelivery)
          } else {
            current
          }
        }
      }
    }
  }

  /**
   * Reloads the export list from disk.
   */
  fun refresh() {
    viewModelScope.launch {
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
    }
  }

  /**
   * Deletes the export at [exportId] and reloads the list.
   */
  fun onDelete(exportId: String) {
    viewModelScope.launch {
      exportManager.deleteExport(exportId)
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
    }
  }

  /**
   * Retries a failed delivery and reloads the list so the refreshed state is visible.
   */
  fun onRetryDelivery(exportId: String) {
    viewModelScope.launch {
      exportManager.retryDelivery(exportId)
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
    }
  }

  /**
   * Sends a fresh delivery email for [exportId] using the archive already in remote storage, then
   * reloads the list so any delivery-state change is visible.
   */
  fun onResendDelivery(exportId: String) {
    viewModelScope.launch {
      exportManager.resendDelivery(exportId)
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
    }
  }
}
