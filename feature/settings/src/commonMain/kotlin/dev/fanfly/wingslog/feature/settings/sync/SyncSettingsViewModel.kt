package dev.fanfly.wingslog.feature.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the dedicated sync settings page. Reads three sources:
 * - [FirebaseAuth.authStateChanged] → tells us whether we're signed in / anonymous (controls
 *   whether sync can be enabled at all).
 * - [dev.fanfly.wingslog.core.sync.data.SyncPreferences.state] → the user's choice.
 * - [dev.fanfly.wingslog.core.sync.data.SyncEngine.failureState] / [dev.fanfly.wingslog.core.sync.data.SyncEngine.hydrationState] → live status for the info panel.
 *
 * The combined [SyncSettingsUiState] is a pure render input.
 */
class SyncSettingsViewModel(
  auth: FirebaseAuth,
  syncEngine: SyncEngine,
  private val syncPreferences: SyncPreferences,
) : ViewModel() {

  val uiState: StateFlow<SyncSettingsUiState> =
    combine(
      auth.authStateChanged,
      syncPreferences.state,
      syncEngine.failureState,
      syncEngine.hydrationState,
    ) { user, prefs, failure, hydration ->
      val signedIn = user != null && !user.isAnonymous
      SyncSettingsUiState(
        signedIn = signedIn,
        cloudSyncEnabled = prefs.cloudSyncEnabled,
        failureMessage = failure?.message,
        hydration = hydration,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = SyncSettingsUiState.Initial,
    )

  fun onCloudSyncToggled(enabled: Boolean) {
    viewModelScope.launch { syncPreferences.setCloudSyncEnabled(enabled) }
  }
}

/** Pure render input for [SyncSettingsScreen]. */
data class SyncSettingsUiState(
  val signedIn: Boolean,
  val cloudSyncEnabled: Boolean,
  val failureMessage: String?,
  val hydration: HydrationState,
) {
  companion object {
    val Initial = SyncSettingsUiState(
      signedIn = false,
      cloudSyncEnabled = true,
      failureMessage = null,
      hydration = HydrationState.Idle,
    )
  }
}
