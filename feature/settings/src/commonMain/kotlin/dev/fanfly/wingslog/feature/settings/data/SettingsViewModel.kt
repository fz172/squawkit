package dev.fanfly.wingslog.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AccountDeleter
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.datamanager.SignOutCoordinator
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Apple Hide My Email hands us an alias at this domain. We know the string; the pilot does not —
 * they have never been shown it — so it is no use as something to ask them to type.
 */
private const val APPLE_PRIVATE_RELAY_DOMAIN = "@privaterelay.appleid.com"

class SettingsViewModel(
  private val authManager: AuthManager,
  private val accountDeleter: AccountDeleter,
  private val attachmentManager: AttachmentManager,
  private val dbChecker: DatabaseIntegrityChecker,
  private val featureLabManager: DeveloperOptionsManager,
  private val appearanceController: AppearanceController,
  private val analyticsPreferenceController: AnalyticsPreferenceController,
  private val appCapability: AppCapability,
  private val adConsentManager: AdConsentManager,
  private val notificationPermission: NotificationPermission,
  private val notificationPrefsManager: NotificationPrefsManager,
  /**
   * Sign-out, with this device's push registration released first. Shared with the
   * corruption-recovery path in `AppEntry`, which had the same sequence to get right and did not
   * (#550) — see [SignOutCoordinator].
   */
  private val signOutCoordinator: SignOutCoordinator,
) : ViewModel() {

  private val _user =
    MutableStateFlow(
      SettingsUiState(
        isDeveloperOptionsSupported = appCapability.isDeveloperOptionsSupported,
        isNotificationsSupported = appCapability.isNotificationsSupported,
      )
    )
  val user: StateFlow<SettingsUiState> = _user.asStateFlow()

  /** Device-local light/dark/system preference, shared with the root theme. */
  val appearanceMode: StateFlow<AppearanceMode> = appearanceController.mode

  fun setAppearance(mode: AppearanceMode) = appearanceController.setMode(mode)

  /** Device-local Firebase Logging (analytics collection) preference, default on. */
  val firebaseLoggingEnabled: StateFlow<Boolean> =
    analyticsPreferenceController.enabled

  fun setFirebaseLoggingEnabled(enabled: Boolean) =
    analyticsPreferenceController.setEnabled(enabled)

  private var observeSelfJob: Job? = null

  init {
    loadUserProfile()
    observeDeveloperFlags()
    refreshAdPrivacyOptionsAvailability()
    observeNotificationsRowState()
  }

  /**
   * `BLOCKED` beats `OFF`: an OS-level denial is true regardless of the in-app master switch, and
   * is the more actionable thing to surface first. While preferences are
   * [PrefsState.Unresolved] this reads as `DEFAULT` — everything defaults on (design §8.3), and a
   * subtitle briefly guessing the common case is a cosmetic risk, not the write-time hazard
   * [PrefsState.Unresolved]'s own doc warns about; nothing here ever writes through this state.
   */
  private fun observeNotificationsRowState() {
    viewModelScope.launch {
      combine(
        notificationPermission.observe(),
        notificationPrefsManager.observe(),
      ) { permission, prefs ->
        when {
          permission == PermissionState.DENIED || permission == PermissionState.UNSUPPORTED ->
            NotificationsRowState.BLOCKED
          prefs is PrefsState.Resolved && !prefs.settings.allEnabled -> NotificationsRowState.OFF
          else -> NotificationsRowState.DEFAULT
        }
      }.collect { rowState ->
        _user.value = _user.value.copy(notificationsRowState = rowState)
      }
    }
  }

  /**
   * Whether "Ad privacy settings" has anything to show right now (#384) — only meaningful once
   * queried, since the underlying CMP call is lazy (see [AdConsentManager]'s KDoc), so this starts
   * `false` at [loadUserProfile] and flips to `true` here if it turns out to be available.
   */
  private fun refreshAdPrivacyOptionsAvailability() {
    if (!appCapability.isAdsSupported) return
    viewModelScope.launch {
      val available = adConsentManager.isPrivacyOptionsAvailable()
      _user.value = _user.value.copy(isAdPrivacyOptionsAvailable = available)
    }
  }

  private fun observeDeveloperFlags() {
    viewModelScope.launch {
      featureLabManager.observe()
        .collect { flags ->
          _user.value = _user.value.copy(featureFlags = flags)
        }
    }
  }

  /**
   * Seeds the state a Settings entry starts from.
   *
   * [SettingsUiState.isAnonymous] has to be read here and not only in [refreshAccountState]: that
   * one runs after a completed upgrade, and the upgrade entry point is itself gated on this flag,
   * so leaving it at its `false` default made a guest look like a permanent account and hid the
   * only control that could have corrected it.
   */
  private fun loadUserProfile() {
    _user.value = SettingsUiState(
      userStatus = UserStatus.LOADING,
      isAnonymous = authManager.getCurrentUser()?.isAnonymous == true,
      isDeveloperOptionsSupported = appCapability.isDeveloperOptionsSupported,
      isNotificationsSupported = appCapability.isNotificationsSupported,
    )
  }

  /** Re-presents the CMP's privacy-options form, for "Ad privacy settings". */
  fun presentAdPrivacyOptions() {
    viewModelScope.launch { adConsentManager.presentPrivacyOptions() }
  }

  /**
   * Re-reads account-derived fields (photo, anonymous flag) from the current Firebase user.
   * Call after an account upgrade: linking does not fire authStateChanged, so the [observeSelf]
   * collector may not re-emit on its own (e.g. when the self-technician name is unchanged).
   */
  fun refreshAccountState() {
    val current = authManager.getCurrentUser()
    _user.value = _user.value.copy(
      isAnonymous = current?.isAnonymous == true,
    )
  }

  /**
   * Opens the confirmation, and fixes what the pilot will have to type to get past it. Nothing is
   * destroyed until [confirmDeleteAccount].
   *
   * The challenge is resolved once, here, rather than read live in the dialog: a sign-out or token
   * refresh mid-confirmation must not swap the target out from under half-typed input.
   */
  fun askToDeleteAccount() {
    if (_user.value.deletion == AccountDeletion.Working) return
    _user.value = _user.value.copy(
      deletion = AccountDeletion.Confirming,
      deletionChallenge = challengeFor(authManager.getCurrentUser()?.email),
      deletionInput = "",
    )
  }

  /**
   * Their own address when we have one they would recognise, the fixed phrase otherwise — a blank
   * address, or an alias from [APPLE_PRIVATE_RELAY_DOMAIN] that they could not type from memory.
   */
  private fun challengeFor(email: String?): DeletionChallenge {
    val address = email?.trim().orEmpty()
    val usable = address.isNotEmpty() &&
      !address.endsWith(APPLE_PRIVATE_RELAY_DOMAIN, ignoreCase = true)
    return if (usable) DeletionChallenge.Email(address) else DeletionChallenge.Phrase
  }

  /**
   * The confirmation text typed so far. Held here rather than in a composable `remember` for the
   * same reason as [AccountDeletion] itself — a recomposition or rotation must not quietly reset
   * how far the pilot has got.
   */
  fun setDeleteAccountInput(text: String) {
    if (_user.value.deletion == AccountDeletion.Working) return
    _user.value = _user.value.copy(deletionInput = text)
  }

  fun cancelDeleteAccount() {
    if (_user.value.deletion == AccountDeletion.Working) return
    _user.value = _user.value.copy(deletion = AccountDeletion.Idle, deletionInput = "")
  }

  /**
   * Deletes the account, then this device's copy of its data (#418).
   *
   * **The order is the whole safety property.** The local wipe runs only after the server confirms
   * the account is gone — on failure the account still exists, and its records may live nowhere
   * else, so wiping would destroy the only copy. That is why [AccountDeleter] returns a boolean
   * rather than failing silently the way a best-effort call would.
   *
   * The Auth user is deleted server-side, so no re-authentication is needed here and the session is
   * already invalid by the time this returns; signing out is what makes the app notice and route
   * back to login.
   *
   * Only call this once the typed [SettingsUiState.deletionChallenge] has been met —
   * `DeleteAccountDialog` enables its confirm button on nothing less, and it is the only caller.
   */
  fun confirmDeleteAccount() {
    if (_user.value.deletion == AccountDeletion.Working) return
    val uid = authManager.getCurrentUser()?.uid ?: return
    _user.value = _user.value.copy(deletion = AccountDeletion.Working)

    viewModelScope.launch {
      if (!accountDeleter.deleteAccount()) {
        _user.value = _user.value.copy(deletion = AccountDeletion.Failed)
        return@launch
      }
      observeSelfJob?.cancel()
      // No clearThisDevice() here, deliberately — unlike logOut(). deleteMyAccount recursive-deletes
      // users/{uid}, and `deleteMyAccount.ts` calls out push_devices as going with it, so every
      // device's doc is already gone rather than just this one's. Worse, the Auth user is deleted
      // server-side, so by this line there is no live uid to authorize the write: the delete could
      // only sit unacknowledged and strand `deletion` on Working behind a spinner.
      // Same ordering as logOut(): sign out first so the SyncEngine releases the write lock the
      // wipes below need, or they block forever on web. authManager directly rather than
      // signOutCoordinator, for the reason above — there is nothing left to clear, and no session
      // left to authorize the attempt.
      authManager.logOut()
      _user.value = SettingsUiState(userStatus = UserStatus.LOGGED_OUT)
      attachmentManager.wipeLocalData(uid)
      dbChecker.wipeDataForUser(uid)
    }
  }

  /**
   * Signs out and removes this account's data from the device.
   *
   * **Only safe for a permanent account**, where the wipe is a local cache drop and the records
   * come back on next login. A guest has no cloud copy, so the same call is an unrecoverable
   * delete of everything they have entered — which is why Settings offers guests "Link to an
   * account" rather than a log out (#413). Anything that ever calls this for a guest must put an
   * explicit erase warning in front of it first.
   */
  fun logOut() {
    val uid = authManager.getCurrentUser()?.uid
    viewModelScope.launch {
      observeSelfJob?.cancel()
      // Clears this device's push registration and THEN signs out, in that order and bounded — the
      // whole of why [SignOutCoordinator] exists is written there, along with what an expired clear
      // leaves behind.
      //
      // It has to complete before the wipes below for a second, unrelated reason: signing out fires
      // authStateChanged(null), which makes the SyncEngine cancel its userScope and release the
      // DatabaseWriteLock. The wipes need that lock — running them first would block forever on web
      // (JS single-thread, SyncEngine holds the lock across suspend points during hydration/push).
      signOutCoordinator.signOut()
      _user.value =
        SettingsUiState(userStatus = UserStatus.LOGGED_OUT)
      if (uid != null) {
        attachmentManager.wipeLocalData(uid)
        dbChecker.wipeDataForUser(uid)
      }
    }
  }

}
