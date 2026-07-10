package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Manage Access screen for one aircraft: combines the caller's locally-resolved role
 * with the (online-only) Firestore roster, and issues owner mutations / leave via [SharingManager].
 */
class ManageAccessViewModel(
  private val sharingManager: SharingManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String = savedStateHandle.get<String>(Screen.AIRCRAFT_ID).orEmpty()

  private val _uiState = MutableStateFlow(ManageAccessUiState())
  val uiState = _uiState.asStateFlow()

  init {
    observeShare()
  }

  private fun observeShare() {
    viewModelScope.launch {
      combine(
        sharingManager.observeMyRole(aircraftId),
        sharingManager.observeShareState(aircraftId),
      ) { role, share -> role to share }
        .catch { e ->
          _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
        .collect { (role, share) ->
          _uiState.update {
            it.copy(
              isLoading = false,
              myRole = role,
              members = share.members,
              error = null,
            )
          }
        }
    }
  }

  fun changeRole(uid: String, role: ShareRole) {
    viewModelScope.launch {
      sharingManager.updateRole(aircraftId, uid, role)
        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }
  }

  fun revoke(uid: String) {
    viewModelScope.launch {
      sharingManager.revokeMember(aircraftId, uid)
        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }
  }

  fun leave() {
    viewModelScope.launch {
      sharingManager.leave(aircraftId)
        .onSuccess { _uiState.update { it.copy(leaveSuccess = true) } }
        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }
}
