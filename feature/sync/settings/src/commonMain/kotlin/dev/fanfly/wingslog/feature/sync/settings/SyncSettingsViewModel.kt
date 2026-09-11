package dev.fanfly.wingslog.feature.sync.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.SyncFailure
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Drives the dedicated sync settings page. Reads three sources:
 * - [FirebaseAuth.authStateChanged] → whether we're signed in / anonymous (controls whether sync
 *   can be enabled at all).
 * - [SyncPreferences.state] → the user's choice.
 * - [SyncEngine.failureState] / [SyncEngine.hydrationState] / [SyncEngine.lastSyncedAt] → live
 *   status for the Status card.
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
      syncEngine.lastSyncedAt,
    ) { user, prefs, failure, hydration, lastSyncedAt ->
      val signedIn = user != null && !user.isAnonymous
      SyncSettingsUiState(
        signedIn = signedIn,
        cloudSyncEnabled = prefs.cloudSyncEnabled,
        allowUploadOnCellular = prefs.allowUploadOnCellular,
        failure = failure,
        hydration = hydration,
        lastSyncedAt = lastSyncedAt,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = SyncSettingsUiState.Initial,
    )

  fun onCloudSyncToggled(enabled: Boolean) {
    viewModelScope.launch { syncPreferences.setCloudSyncEnabled(enabled) }
  }

  fun onAllowUploadOnCellularToggled(allowed: Boolean) {
    viewModelScope.launch { syncPreferences.setAllowUploadOnCellular(allowed) }
  }
}

/** Pure render input for [SyncSettingsScreen]. */
data class SyncSettingsUiState(
  val signedIn: Boolean,
  val cloudSyncEnabled: Boolean,
  val allowUploadOnCellular: Boolean,
  val failure: SyncFailure?,
  val hydration: HydrationState,
  /** See [SyncEngine.lastSyncedAt]. */
  val lastSyncedAt: Instant?,
) {
  companion object {
    val Initial = SyncSettingsUiState(
      signedIn = false,
      cloudSyncEnabled = true,
      allowUploadOnCellular = false,
      failure = null,
      hydration = HydrationState.Idle,
      lastSyncedAt = null,
    )
  }
}
