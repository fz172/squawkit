package dev.fanfly.wingslog.feature.sync.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryEmailSource
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryInfo
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.SyncFailure
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
  featureLabManager: FeatureLabManager,
) : ViewModel() {

  private val _attachmentEnabled = MutableStateFlow(true)

  val uiState: StateFlow<SyncSettingsUiState> =
    combine(
      auth.authStateChanged,
      syncPreferences.state,
      syncEngine.failureState,
      syncEngine.hydrationState,
      _attachmentEnabled,
    ) { user, prefs, failure, hydration, attachmentEnabled ->
      val signedIn = user != null && !user.isAnonymous
      val explicitEmail = prefs.exportDestinationEmail.trim()
      val authEmail = user?.email.orEmpty().trim()
      SyncSettingsUiState(
        signedIn = signedIn,
        cloudSyncEnabled = prefs.cloudSyncEnabled,
        allowUploadOnCellular = prefs.allowUploadOnCellular,
        exportDestinationEmail = prefs.exportDestinationEmail,
        resolvedExportDelivery = when {
          !signedIn -> null
          explicitEmail.isNotBlank() -> ExportDeliveryInfo(explicitEmail, ExportDeliveryEmailSource.EXPLICIT)
          authEmail.isNotBlank() -> ExportDeliveryInfo(authEmail, ExportDeliveryEmailSource.AUTH_FALLBACK)
          else -> null
        },
        failure = failure,
        hydration = hydration,
        attachmentEnabled = attachmentEnabled,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
      initialValue = SyncSettingsUiState.Initial,
    )

  init {
    viewModelScope.launch {
      featureLabManager.observe()
        .collect { flags ->
          _attachmentEnabled.update { flags.attachmentUploadEnabled }
        }
    }
  }

  fun onCloudSyncToggled(enabled: Boolean) {
    viewModelScope.launch { syncPreferences.setCloudSyncEnabled(enabled) }
  }

  fun onAllowUploadOnCellularToggled(allowed: Boolean) {
    viewModelScope.launch { syncPreferences.setAllowUploadOnCellular(allowed) }
  }

  fun onExportDestinationEmailChanged(email: String) {
    viewModelScope.launch { syncPreferences.setExportDestinationEmail(email) }
  }
}

/** Pure render input for [SyncSettingsScreen]. */
data class SyncSettingsUiState(
  val signedIn: Boolean,
  val cloudSyncEnabled: Boolean,
  val allowUploadOnCellular: Boolean,
  val exportDestinationEmail: String,
  val resolvedExportDelivery: ExportDeliveryInfo?,
  val failure: SyncFailure?,
  val hydration: HydrationState,
  val attachmentEnabled: Boolean = true,
) {
  companion object {
    val Initial = SyncSettingsUiState(
      signedIn = false,
      cloudSyncEnabled = true,
      allowUploadOnCellular = false,
      exportDestinationEmail = "",
      resolvedExportDelivery = null,
      failure = null,
      hydration = HydrationState.Idle,
    )
  }
}
