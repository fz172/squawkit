package dev.fanfly.wingslog.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * R1 sync user preferences. Two flags surfaced via the dedicated sync settings page:
 *
 * - [cloudSyncEnabled] — master toggle. When false, [SyncEngine.start] stays idle even if a
 *   non-anonymous user is signed in.
 * - [showSyncActivity] — UI-only flag for whether the dashboard surfaces hydration progress and
 *   "items not synced" banners. Doesn't influence the engine.
 *
 * R1 keeps this in-memory; persistence (DataStore on Android, NSUserDefaults on iOS) lands as a
 * follow-up. Defaults match a fresh install: sync on, activity visible.
 */
data class SyncPrefs(
  val cloudSyncEnabled: Boolean = true,
  val showSyncActivity: Boolean = true,
)

class SyncPreferences {

  private val _state = MutableStateFlow(SyncPrefs())

  val state: StateFlow<SyncPrefs> = _state.asStateFlow()

  fun setCloudSyncEnabled(enabled: Boolean) {
    _state.update { it.copy(cloudSyncEnabled = enabled) }
  }

  fun setShowSyncActivity(enabled: Boolean) {
    _state.update { it.copy(showSyncActivity = enabled) }
  }
}
