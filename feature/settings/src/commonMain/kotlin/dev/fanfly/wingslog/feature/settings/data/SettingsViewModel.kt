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
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
   * Null on any platform with no push transport — iOS until P5, web by design (§8). Nullable rather
   * than a no-op binding so "this platform does not register tokens" is visible here rather than
   * hidden behind a stub that looks like it did something.
   */
  private val pushTokenRegistrar: PushTokenRegistrar? = null,
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

  /** Opens the confirmation. Nothing is destroyed until [confirmDeleteAccount]. */
  fun askToDeleteAccount() {
    if (_user.value.deletion == AccountDeletion.Working) return
    _user.value = _user.value.copy(deletion = AccountDeletion.Confirming)
  }

  fun cancelDeleteAccount() {
    if (_user.value.deletion == AccountDeletion.Working) return
    _user.value = _user.value.copy(deletion = AccountDeletion.Idle)
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
      // Before logOut(), for the reason logOut() explains: deleting the token doc needs a live uid.
      // Belt-and-braces here — deleteMyAccount already recursive-deletes users/{uid}, push_devices
      // included — but this account may have been signed in on other devices, and the local one is
      // the only doc this device is able to remove.
      pushTokenRegistrar?.clearThisDevice()
      // Same ordering as logOut(): sign out first so the SyncEngine releases the write lock the
      // wipes below need, or they block forever on web.
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
      // BEFORE logOut, and deliberately the opposite order from the wipes below. Deleting
      // users/{uid}/push_devices/{installId} is a Firestore write that rules gate on
      // `request.auth.uid == userId`, so once the session is gone it is permission-denied and the
      // token survives — leaving this device receiving another account's squawk titles in its tray
      // (design §7.1), which is exactly the leak urgency_watermark's sign-out wipe closed locally.
      // It runs before the lock-release concern below because it touches Firestore, not SQLDelight.
      pushTokenRegistrar?.clearThisDevice()
      // Sign out first so authStateChanged(null) fires immediately, which causes the SyncEngine
      // to cancel its userScope and release the DatabaseWriteLock. The wipe operations below
      // need that lock — calling them before signOut would block forever on web (JS single-thread,
      // SyncEngine holds the lock across suspend points during active hydration/push).
      authManager.logOut()
      _user.value =
        SettingsUiState(userStatus = UserStatus.LOGGED_OUT)
      if (uid != null) {
        attachmentManager.wipeLocalData(uid)
        dbChecker.wipeDataForUser(uid)
      }
    }
  }
}
