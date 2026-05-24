package dev.fanfly.wingslog.feature.export.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryOutcome
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads previously generated export archives and supports removing them.
 */
class ExportHistoryViewModel(
  private val exportManager: ExportManager,
  private val auth: FirebaseAuth,
) : ViewModel() {

  private val _state =
    MutableStateFlow<ExportHistoryUiState>(ExportHistoryUiState.Loading)
  val state: StateFlow<ExportHistoryUiState> = _state.asStateFlow()

  // One-shot delivery outcomes (resend/retry) for the UI to surface as a snackbar.
  private val _deliveryEvents = Channel<ExportDeliveryOutcome>()
  val deliveryEvents = _deliveryEvents.receiveAsFlow()

  // Email-account users share by re-sending the delivery email; everyone else uses the device sheet.
  private var canEmailDelivery: Boolean = false
  private var observedUid: String? = null

  init {
    observeAuth()
    refresh()
  }

  private fun observeAuth() {
    viewModelScope.launch {
      auth.authStateChanged.collect { user ->
        val uid = user?.uid
        canEmailDelivery =
          user != null && !user.isAnonymous && !user.email.isNullOrBlank()
        if (uid != observedUid) {
          observedUid = uid
          _state.value = ExportHistoryUiState.Loaded(
            exportManager.listExports(),
            canEmailDelivery,
          )
        } else {
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
      val outcome = exportManager.retryDelivery(exportId)
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
      _deliveryEvents.send(outcome)
    }
  }

  /**
   * Sends a fresh delivery email for [exportId] using the archive already in remote storage, then
   * reloads the list so any delivery-state change is visible.
   */
  fun onResendDelivery(exportId: String) {
    viewModelScope.launch {
      val outcome = exportManager.resendDelivery(exportId)
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
      _deliveryEvents.send(outcome)
    }
  }

  /**
   * Downloads a remote-only export to this device, then reloads so it surfaces as on-device.
   */
  fun onSaveToDevice(exportId: String) {
    viewModelScope.launch {
      exportManager.saveToDevice(exportId)
      _state.value = ExportHistoryUiState.Loaded(
        exportManager.listExports(),
        canEmailDelivery
      )
    }
  }
}
