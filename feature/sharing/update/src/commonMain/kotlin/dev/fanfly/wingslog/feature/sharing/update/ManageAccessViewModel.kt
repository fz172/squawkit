package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

  val aircraftId: String = savedStateHandle.get<String>(Screen.AIRCRAFT_ID).orEmpty()

  private val _uiState = MutableStateFlow(ManageAccessUiState())
  val uiState = _uiState.asStateFlow()

  private val logger = Logger.withTag("ManageAccessViewModel")

  init {
    observeShare()
  }

  private fun observeShare() {
    // Role is resolved locally (own aircraft ⇒ owner, shared ⇒ ref) — always available, and what
    // gates the Invite action. Kept separate from the roster so it never depends on it.
    viewModelScope.launch {
      sharingManager.observeMyRole(aircraftId).collect { role ->
        _uiState.update { it.copy(isLoading = false, myRole = role) }
      }
    }
    // The roster is online-only and, for an aircraft that hasn't been shared yet, not readable at
    // all (the owner isn't in memberRoles until the first invite bootstraps the share doc). Treat a
    // failure as "no members yet" rather than an error, so it can't block the owner from inviting.
    viewModelScope.launch {
      sharingManager.observeShareState(aircraftId)
        .catch { e ->
          // Roster unavailable (e.g. no share yet) — leave it empty, don't block inviting.
          logger.d { "share roster unavailable for $aircraftId: ${e.message}" }
        }
        .collect { share -> _uiState.update { it.copy(members = share.members) } }
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
