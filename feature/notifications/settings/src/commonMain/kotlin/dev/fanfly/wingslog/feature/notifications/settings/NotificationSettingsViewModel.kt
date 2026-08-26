package dev.fanfly.wingslog.feature.notifications.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.model.withAircraftActivity
import dev.fanfly.wingslog.feature.notifications.model.withAllEnabled
import dev.fanfly.wingslog.feature.notifications.model.withAog
import dev.fanfly.wingslog.feature.notifications.model.withDueSoon
import dev.fanfly.wingslog.feature.notifications.model.withLogActivity
import dev.fanfly.wingslog.feature.notifications.model.withOverdue
import dev.fanfly.wingslog.feature.notifications.model.withSquawkActivity
import dev.fanfly.wingslog.feature.notifications.model.withSquawkPriority
import dev.fanfly.wingslog.feature.notifications.model.withTaskActivity
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Design §9.2. Combines six independently-changing sources into one render input; the screen
 * itself never reads any of them directly.
 *
 * [NotificationSettingsUiState.isLoading] is true, and every toggle must be **disabled** (not just
 * dimmed) while it is, in two separate situations: before this flow's own `stateIn` seed has been
 * replaced by a real `combine` emission, and while [PrefsState.Unresolved] — preferences exist
 * server-side but this device has not read them yet. The second one has teeth: an editable toggle
 * rendered against a guessed value writes that guess back through [NotificationPrefsManager.update]
 * as a whole-message overwrite, silently reverting the user's real settings on every other device
 * (§4.3). The spinner is cosmetic; the disabled toggle is what makes that unreachable.
 */
class NotificationSettingsViewModel(
  private val prefsManager: NotificationPrefsManager,
  private val permission: NotificationPermission,
  auth: FirebaseAuth,
  syncPreferences: SyncPreferences,
) : ViewModel() {

  /**
   * Q5's confirm gate, held here rather than composable `remember`: the OS permission dialog can
   * tear this screen down mid-decision, and a `remember`ed flag would silently reset to "not
   * confirming" underneath it.
   */
  private val confirmDisableAog = MutableStateFlow(false)

  /** One-shot: set on a failed [NotificationPrefsManager.update], cleared by [onSaveErrorShown]. */
  private val saveError = MutableStateFlow(false)

  val uiState: StateFlow<NotificationSettingsUiState> =
    combine(
      prefsManager.observe(),
      permission.observe(),
      auth.authStateChanged,
      syncPreferences.state,
      // combine() tops out at 5 typed flows — folding these two together keeps the outer combine
      // at arity 5 instead of falling through to the untyped Array<Any?> vararg overload.
      combine(
        confirmDisableAog,
        saveError
      ) { confirming, error -> confirming to error },
    ) { prefs, permissionState, user, syncPrefs, (confirming, error) ->
      NotificationSettingsUiState(
        settings = (prefs as? PrefsState.Resolved)?.settings
          ?: NotificationSettings(),
        permission = permissionState,
        canOpenSystemSettings = permission.canOpenSystemSettings,
        isSignedIn = user != null && !user.isAnonymous,
        isCloudSyncEnabled = syncPrefs.cloudSyncEnabled,
        isLoading = prefs is PrefsState.Unresolved,
        confirmDisableAog = confirming,
        saveError = error,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = NotificationSettingsUiState(),
    )

  fun onSaveErrorShown() {
    saveError.value = false
  }

  fun onOpenSystemSettings() {
    permission.openSystemSettings()
  }

  /**
   * The app-wide switch behind [dev.fanfly.wingslog.feature.notifications.model.allEnabled] — the
   * thing [dev.fanfly.wingslog.feature.settings.data.NotificationsRowState.OFF] on the Settings row
   * refers to. Design §9.3's UNDETERMINED row says flipping it on "triggers the OS prompt inline";
   * this is the only master control on the screen, so that behavior lives here.
   */
  fun onAllNotificationsToggled(enabled: Boolean) {
    if (enabled) {
      viewModelScope.launch {
        if (uiState.value.permission == PermissionState.UNDETERMINED) permission.request()
        write { it.withAllEnabled(true) }
      }
      return
    }
    update { it.withAllEnabled(false) }
  }

  fun onAogToggled(enabled: Boolean) {
    if (!enabled) {
      confirmDisableAog.value = true
      return
    }
    update { it.withAog(true) }
  }

  fun onConfirmDisableAog() {
    confirmDisableAog.value = false
    update { it.withAog(false) }
  }

  fun onDismissDisableAog() {
    confirmDisableAog.value = false
  }

  fun onSquawkPriorityToggled(enabled: Boolean) =
    update { it.withSquawkPriority(enabled) }

  fun onOverdueToggled(enabled: Boolean) = update { it.withOverdue(enabled) }
  fun onDueSoonToggled(enabled: Boolean) = update { it.withDueSoon(enabled) }

  fun onAircraftActivityToggled(enabled: Boolean) =
    update { it.withAircraftActivity(enabled) }

  fun onSquawkActivityToggled(enabled: Boolean) =
    update { it.withSquawkActivity(enabled) }

  fun onTaskActivityToggled(enabled: Boolean) =
    update { it.withTaskActivity(enabled) }

  fun onLogActivityToggled(enabled: Boolean) =
    update { it.withLogActivity(enabled) }

  private fun update(mutate: (NotificationSettings) -> NotificationSettings) {
    viewModelScope.launch { write(mutate) }
  }

  private suspend fun write(mutate: (NotificationSettings) -> NotificationSettings) {
    if (prefsManager.update(mutate).isFailure) saveError.value = true
  }
}
