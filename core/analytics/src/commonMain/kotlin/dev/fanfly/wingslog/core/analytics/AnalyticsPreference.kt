package dev.fanfly.wingslog.core.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local persistence for the Firebase Logging (analytics collection) preference; implemented
 * per platform. Device-local, not synced per account, mirroring [dev.fanfly.wingslog.core.ui.theme.AppearanceStore].
 */
interface AnalyticsPreferenceStore {
  fun load(): Boolean
  fun save(enabled: Boolean)
}

/**
 * Holds the Firebase Logging on/off preference as observable state, seeded from
 * [AnalyticsPreferenceStore] and applied to [AnalyticsManager] on creation and on every change.
 * Registered as an eager singleton (`createdAtStart = true`) so collection is disabled at launch
 * if the user previously turned it off, even before Settings is opened.
 */
class AnalyticsPreferenceController(
  private val store: AnalyticsPreferenceStore,
  private val analyticsManager: AnalyticsManager,
) {
  private val _enabled = MutableStateFlow(store.load())
  val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

  init {
    analyticsManager.setAnalyticsCollectionEnabled(_enabled.value)
  }

  fun setEnabled(enabled: Boolean) {
    if (_enabled.value == enabled) return
    _enabled.value = enabled
    store.save(enabled)
    analyticsManager.setAnalyticsCollectionEnabled(enabled)
  }
}
