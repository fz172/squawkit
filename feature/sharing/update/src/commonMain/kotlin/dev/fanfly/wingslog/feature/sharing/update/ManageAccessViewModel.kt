package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
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
  private val cloudSync: CloudSyncSetting,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val aircraftId: String = savedStateHandle.get<String>(Screen.AIRCRAFT_ID)
    .orEmpty()

  private val _uiState =
    MutableStateFlow(ManageAccessUiState(syncEnabled = cloudSync.isCloudSyncEnabled()))
  val uiState = _uiState.asStateFlow()

  private val logger = Logger.withTag("ManageAccessViewModel")

  init {
    observeShare()
    // Self-heal a missing member doc. The roster's membership comes from the ACL, but names and
    // photos come from the member docs — so a member whose doc is absent renders as a bare uid. This
    // is idempotent and cheap, and it means opening the screen repairs the row rather than staring
    // at the damage. (§7.2)
    viewModelScope.launch { sharingManager.publishTechnicianMirror(alsoPublishTo = aircraftId) }
  }

  private fun observeShare() {
    // Role is resolved locally (own aircraft ⇒ owner, shared ⇒ ref) — always available, and what
    // gates the Invite action. Kept separate from the roster so it never depends on it.
    viewModelScope.launch {
      sharingManager.observeMyRole(aircraftId)
        .collect { role ->
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
        .collect { share ->
          if (share.members.any { it.isSelf }) seenSelfInRoster = true

          // A denial *after* we have seen ourselves on this roster is a revocation: the owner just
          // removed us and the rules cut the listener off. Leaving the screen open would show a
          // stale roster that still lists us as a member of a share we no longer belong to, so it
          // closes — the same exit as leaving voluntarily. A denial *before* we ever appeared is
          // just an owner whose aircraft has no share doc yet, and means nothing.
          if (share.accessDenied) {
            if (seenSelfInRoster) {
              logger.i { "access to $aircraftId was revoked; closing Manage Access" }
              _uiState.update { it.copy(accessRevoked = true) }
            }
            return@collect
          }
          _uiState.update { it.copy(members = share.members) }
        }
    }
  }

  /**
   * Whether this user has ever appeared on this aircraft's roster. It is what makes a later denial
   * legible: revocation and "no share exists yet" are the same PERMISSION_DENIED on the wire, and
   * only having-been-a-member tells them apart.
   */
  private var seenSelfInRoster = false

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
