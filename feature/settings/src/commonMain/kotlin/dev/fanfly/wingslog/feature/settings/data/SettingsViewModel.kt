package dev.fanfly.wingslog.feature.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.AppearanceMode
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val authManager: AuthManager,
  private val technicianManager: TechnicianManager,
  private val attachmentManager: AttachmentManager,
  private val dbChecker: DatabaseIntegrityChecker,
  private val featureLabManager: FeatureLabManager,
  private val appearanceController: AppearanceController,
  private val analyticsPreferenceController: AnalyticsPreferenceController,
  private val appCapability: AppCapability,
) : ViewModel() {

  private val _user =
    MutableStateFlow(SettingsUiState(isFeatureLabSupported = appCapability.isFeatureLabSupported))
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
    observeFeatureFlags()
  }

  private fun observeFeatureFlags() {
    viewModelScope.launch {
      featureLabManager.observe()
        .collect { flags ->
          _user.value = _user.value.copy(featureFlags = flags)
        }
    }
  }

  private fun loadUserProfile() {
    _user.value = SettingsUiState(
      userStatus = UserStatus.LOADING,
      isFeatureLabSupported = appCapability.isFeatureLabSupported,
    )
    observeSelfJob?.cancel()
    observeSelfJob = viewModelScope.launch {
      technicianManager.observeSelf()
        .collect { self ->
          _user.value = _user.value.copy(
            photoUri = authManager.getCurrentUser()?.photoURL,
            selfTechnician = self,
            userStatus = UserStatus.LOGGED_IN,
            isAnonymous = authManager.getCurrentUser()?.isAnonymous == true,
          )
        }
    }
  }

  /**
   * Re-reads account-derived fields (photo, anonymous flag) from the current Firebase user.
   * Call after an account upgrade: linking does not fire authStateChanged, so the [observeSelf]
   * collector may not re-emit on its own (e.g. when the self-technician name is unchanged).
   */
  fun refreshAccountState() {
    val current = authManager.getCurrentUser()
    _user.value = _user.value.copy(
      photoUri = current?.photoURL,
      isAnonymous = current?.isAnonymous == true,
    )
  }

  fun logOut() {
    val uid = authManager.getCurrentUser()?.uid
    viewModelScope.launch {
      observeSelfJob?.cancel()
      // Sign out first so authStateChanged(null) fires immediately, which causes the SyncEngine
      // to cancel its userScope and release the DatabaseWriteLock. The wipe operations below
      // need that lock — calling them before signOut would block forever on web (JS single-thread,
      // SyncEngine holds the lock across suspend points during active hydration/push).
      authManager.logOut()
      _user.value =
        SettingsUiState(photoUri = null, userStatus = UserStatus.LOGGED_OUT)
      if (uid != null) {
        attachmentManager.wipeLocalData(uid)
        dbChecker.wipeDataForUser(uid)
      }
    }
  }
}
