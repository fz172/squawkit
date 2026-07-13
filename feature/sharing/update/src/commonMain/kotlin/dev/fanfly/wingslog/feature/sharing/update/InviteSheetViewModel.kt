package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.InviteSheetUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the invite sheet for one aircraft: role selection, minting a single-use invite link, and
 * cancelling pending invites (the live roster comes from [SharingManager.observeShareState]).
 */
class InviteSheetViewModel(
  private val sharingManager: SharingManager,
  private val fleetManager: FleetManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String = savedStateHandle.get<String>(Screen.AIRCRAFT_ID).orEmpty()

  private val _uiState = MutableStateFlow(InviteSheetUiState())
  val uiState = _uiState.asStateFlow()

  init {
    observeInvites()
    observeAircraftLabel()
  }

  /**
   * What the invitee is shown before accepting (#201). It has to be carried on the invite: the
   * server cannot read it out of the aircraft record, which is opaque proto bytes.
   */
  private var aircraftLabel: String = ""

  private fun observeAircraftLabel() {
    viewModelScope.launch {
      fleetManager.loadAircraft(aircraftId)
        .catch { aircraftLabel = "" }
        .collect { aircraft ->
          aircraftLabel = aircraft?.let {
            listOf(it.tail_number, listOf(it.make, it.model).filter(String::isNotBlank).joinToString(" "))
              .filter(String::isNotBlank)
              .joinToString(" · ")
          }.orEmpty()
        }
    }
  }

  private fun observeInvites() {
    viewModelScope.launch {
      sharingManager.observeShareState(aircraftId)
        .catch { e -> _uiState.update { it.copy(error = e.message) } }
        .collect { share -> _uiState.update { it.copy(pendingInvites = share.invites) } }
    }
  }

  fun selectRole(role: ShareRole) {
    _uiState.update { it.copy(selectedRole = role) }
  }

  fun createInvite() {
    if (_uiState.value.creating) return
    _uiState.update { it.copy(creating = true, error = null) }
    viewModelScope.launch {
      sharingManager.createInvite(aircraftId, _uiState.value.selectedRole, aircraftLabel)
        // Auto-expand the new invite in the pending list once the roster flow delivers it.
        .onSuccess { link -> _uiState.update { it.copy(creating = false, expandedToken = link.codeId) } }
        .onFailure { e -> _uiState.update { it.copy(creating = false, error = e.message) } }
    }
  }

  /** Expand a pending invite's code/QR detail (collapse if it's already the expanded one). */
  fun toggleExpand(codeId: String) {
    _uiState.update {
      it.copy(expandedToken = if (it.expandedToken == codeId) null else codeId)
    }
  }

  fun cancelInvite(codeId: String) {
    viewModelScope.launch {
      sharingManager.cancelInvite(aircraftId, codeId)
        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }
}
