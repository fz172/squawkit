package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.viewing.RedeemUiState
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the aircraft-invite redemption surface (docs/sharing §3.2). Observes the parked deep link
 * ([AircraftShareDeepLinks]) alongside auth state so a signed-out/guest redeemer resumes
 * automatically once they sign in with a real account, then calls the redeem function on Accept.
 *
 * The freshly-written `shared_aircraft_ref` arrives through the always-on refs pull listener, so no
 * explicit resync is issued here — the aircraft shows up in the fleet on the next pull.
 */
class RedeemViewModel(
  private val sharingManager: SharingManager,
  private val auth: FirebaseAuth,
  private val appCapability: AppCapability,
) : ViewModel() {

  private val _uiState = MutableStateFlow<RedeemUiState>(RedeemUiState.Hidden)
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      combine(AircraftShareDeepLinks.pendingInvite, auth.authStateChanged) { invite, user ->
        invite to user
      }.collect { (invite, user) ->
        when {
          invite == null -> _uiState.value = RedeemUiState.Hidden

          // Sharing is gated off in this build (#134). Consume the link and say nothing: a hidden
          // door that still opens is not a gate, and parking the invite would surface the sheet the
          // moment the flag flipped — long after the user tapped anything.
          !appCapability.isAircraftSharingSupported -> {
            AircraftShareDeepLinks.consume()
            _uiState.value = RedeemUiState.Hidden
          }
          // Hold a resolved/in-flight outcome until the user dismisses it.
          _uiState.value.isHeld() -> Unit
          user == null -> _uiState.value = RedeemUiState.Hidden // still on the sign-in screen
          user.isAnonymous -> _uiState.value = RedeemUiState.NeedsSignIn
          else -> {
            _uiState.value = RedeemUiState.Confirm()
            loadPreview(invite.code)
          }
        }
      }
    }
  }

  /**
   * Resolves the code to what the invitee needs to decide (#201). Best-effort: a failure leaves the
   * sheet on its generic copy rather than blocking Accept — a lookup that exists to inform must not
   * become a gate. It is NOT retried, and not called per keystroke: preview spends the same
   * failed-attempt budget as redeem (#164), so hammering it would lock the user out of their own
   * invite.
   */
  private fun loadPreview(code: String) {
    viewModelScope.launch {
      sharingManager.previewInvite(code)
        .onSuccess { preview ->
          val current = _uiState.value
          if (current is RedeemUiState.Confirm) {
            _uiState.value = RedeemUiState.Confirm(preview)
          }
        }
    }
  }

  fun accept() {
    val invite = AircraftShareDeepLinks.pendingInvite.value ?: return
    // Redeem requires a real (non-anonymous) account.
    if (auth.currentUser?.isAnonymous != false) {
      _uiState.value = RedeemUiState.NeedsSignIn
      return
    }
    _uiState.value = RedeemUiState.Redeeming
    viewModelScope.launch {
      sharingManager.redeemInvite(invite.code)
        .onSuccess { outcome ->
          // Every redeemer publishes their mirror into the share they just joined (§7.2) — Owner
          // or Technician alike, since the picker lists membership-with-mirror, not role. Failure
          // is queued in the outbox, so it must not gate the success state.
          //
          // Name the aircraft explicitly: its SharedAircraftRef is still syncing down, so a publish
          // that only consulted local membership would skip the very share we just joined, leaving
          // the redeem function's auth-token name on the member doc for both accounts to see.
          sharingManager.publishTechnicianMirror(alsoPublishTo = outcome.aircraftId)
          _uiState.value =
            if (outcome.alreadyMember) RedeemUiState.AlreadyMember
            else RedeemUiState.Success(outcome.role)
        }
        .onFailure { e -> _uiState.value = RedeemUiState.Failed(e.message) }
    }
  }

  fun dismiss() {
    AircraftShareDeepLinks.consume()
    _uiState.value = RedeemUiState.Hidden
  }
}

/** True while an outcome/spinner should stay put regardless of auth/invite emissions. */
private fun RedeemUiState.isHeld(): Boolean =
  this is RedeemUiState.Redeeming ||
    this is RedeemUiState.Success ||
    this is RedeemUiState.AlreadyMember ||
    this is RedeemUiState.Failed
