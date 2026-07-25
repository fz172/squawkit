package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.AccessPanelView
import dev.fanfly.wingslog.feature.sharing.viewing.AccessToast
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Manage Access panel for one aircraft: the (online-only) Firestore roster + pending
 * invites, the caller's locally-resolved role, and every owner mutation — invite, cancel, change
 * role, revoke, leave — via [SharingManager]. Also owns which of the panel's four steps (people →
 * role → code → member, squawkit#269) is currently showing.
 */
class ManageAccessViewModel(
  private val sharingManager: SharingManager,
  private val cloudSync: CloudSyncSetting,
  private val subscriptionManager: SubscriptionManager,
  private val fleetManager: FleetManager,
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
    observeAircraftLabel()
    // The host-a-share gate. Default-open while the subscription capability is off; when locked, the
    // route surfaces the "Create invite code" action as a promo. Leaving/managing an existing share
    // is unaffected.
    viewModelScope.launch {
      subscriptionManager.canHostShare()
        .collect { canHost -> _uiState.update { it.copy(canHostShare = canHost) } }
    }
    // Self-heal a missing member doc. The roster's membership comes from the ACL, but names and
    // photos come from the member docs — so a member whose doc is absent renders as a bare uid. This
    // is idempotent and cheap, and it means opening the screen repairs the row rather than staring
    // at the damage. (§7.2)
    viewModelScope.launch { sharingManager.publishTechnicianMirror(alsoPublishTo = aircraftId) }
  }

  private fun observeShare() {
    // Role is resolved locally (own aircraft ⇒ owner, shared ⇒ ref) — always available, and what
    // gates the "Create invite code" action. Kept separate from the roster so it never depends on it.
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
          _uiState.update { it.copy(members = share.members, invites = share.invites) }
        }
    }
  }

  /**
   * What the invitee is shown before accepting (#201). It has to be carried on the invite: the
   * server cannot read it out of the aircraft record, which is opaque proto bytes.
   */
  private fun observeAircraftLabel() {
    viewModelScope.launch {
      fleetManager.loadAircraft(aircraftId)
        .catch { }
        .collect { aircraft ->
          val label = aircraft?.let {
            listOf(it.tail_number, listOf(it.make, it.model).filter(String::isNotBlank).joinToString(" "))
              .filter(String::isNotBlank)
              .joinToString(" · ")
          }.orEmpty()
          _uiState.update { it.copy(aircraftLabel = label) }
        }
    }
  }

  /**
   * Whether this user has ever appeared on this aircraft's roster. It is what makes a later denial
   * legible: revocation and "no share exists yet" are the same PERMISSION_DENIED on the wire, and
   * only having-been-a-member tells them apart.
   */
  private var seenSelfInRoster = false

  // --- Panel navigation (main → invite → code → member) ---

  fun openInvite() {
    _uiState.update { it.copy(view = AccessPanelView.INVITE) }
  }

  fun openCode(codeId: String) {
    _uiState.update { it.copy(view = AccessPanelView.CODE, activeInviteCodeId = codeId) }
  }

  fun openMember(uid: String) {
    _uiState.update { it.copy(view = AccessPanelView.MEMBER, activeMemberUid = uid) }
  }

  fun backToMain() {
    _uiState.update {
      it.copy(view = AccessPanelView.MAIN, activeInviteCodeId = null, activeMemberUid = null)
    }
  }

  // --- Invite creation ---

  fun selectInviteRole(role: ShareRole) {
    _uiState.update { it.copy(selectedInviteRole = role) }
  }

  fun createInvite() {
    if (_uiState.value.creatingInvite) return
    _uiState.update { it.copy(creatingInvite = true, error = null) }
    viewModelScope.launch {
      sharingManager.createInvite(aircraftId, _uiState.value.selectedInviteRole, _uiState.value.aircraftLabel)
        .onSuccess { link ->
          _uiState.update {
            it.copy(creatingInvite = false, view = AccessPanelView.CODE, activeInviteCodeId = link.codeId)
          }
        }
        .onFailure { e -> _uiState.update { it.copy(creatingInvite = false, error = e.message) } }
    }
  }

  fun cancelInvite(codeId: String) {
    if (_uiState.value.cancellingInvite) return
    _uiState.update { it.copy(cancellingInvite = true, error = null) }
    viewModelScope.launch {
      sharingManager.cancelInvite(aircraftId, codeId)
        .onSuccess {
          _uiState.update {
            it.copy(
              cancellingInvite = false,
              view = AccessPanelView.MAIN,
              activeInviteCodeId = null,
              toast = AccessToast.CODE_CANCELLED,
            )
          }
        }
        .onFailure { e -> _uiState.update { it.copy(cancellingInvite = false, error = e.message) } }
    }
  }

  fun onInviteLinkCopied() {
    _uiState.update { it.copy(toast = AccessToast.LINK_COPIED) }
  }

  fun toggleHelp() {
    _uiState.update { it.copy(helpExpanded = !it.helpExpanded) }
  }

  // --- Roster mutations ---

  fun changeRole(uid: String, role: ShareRole) {
    viewModelScope.launch {
      sharingManager.updateRole(aircraftId, uid, role)
        .onSuccess { _uiState.update { it.copy(toast = AccessToast.ROLE_UPDATED) } }
        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }
  }

  fun revoke(uid: String) {
    viewModelScope.launch {
      sharingManager.revokeMember(aircraftId, uid)
        .onSuccess {
          _uiState.update {
            it.copy(view = AccessPanelView.MAIN, activeMemberUid = null, toast = AccessToast.ACCESS_REMOVED)
          }
        }
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

  fun clearToast() {
    _uiState.update { it.copy(toast = null) }
  }
}
