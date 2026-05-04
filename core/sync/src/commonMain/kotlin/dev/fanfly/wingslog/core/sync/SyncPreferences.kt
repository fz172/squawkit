package dev.fanfly.wingslog.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * R1 sync user preferences. One flag, surfaced via the dedicated sync settings page:
 *
 * - [cloudSyncEnabled] — master toggle. When false, [SyncEngine.start] stays idle even if a
 *   non-anonymous user is signed in.
 *
 * R1 keeps this in-memory; persistence (DataStore on Android, NSUserDefaults on iOS) lands as a
 * follow-up. Default matches a fresh install: sync on.
 */
data class SyncPrefs(val cloudSyncEnabled: Boolean = true)

class SyncPreferences {

  private val _state = MutableStateFlow(SyncPrefs())

  val state: StateFlow<SyncPrefs> = _state.asStateFlow()

  fun setCloudSyncEnabled(enabled: Boolean) {
    _state.update { it.copy(cloudSyncEnabled = enabled) }
  }
}
