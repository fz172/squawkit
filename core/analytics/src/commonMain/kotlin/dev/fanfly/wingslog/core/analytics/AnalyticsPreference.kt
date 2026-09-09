package dev.fanfly.wingslog.core.analytics

import dev.fanfly.wingslog.core.crash.CrashReporter
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
 * [AnalyticsPreferenceStore] and applied on creation and on every change.
 * Registered as an eager singleton (`createdAtStart = true`) so collection is disabled at launch
 * if the user previously turned it off, even before Settings is opened.
 *
 * One preference, two sinks: the Settings row it backs offers to "share anonymous diagnostics and
 * usage data", so it governs [CrashReporter] alongside [AnalyticsManager] rather than crash reports
 * continuing to upload from someone who has just turned diagnostics off. Crashlytics applies a
 * *disable* on the next run of the app (see [CrashReporter.setCrashCollectionEnabled]); analytics
 * stops immediately.
 */
class AnalyticsPreferenceController(
  private val store: AnalyticsPreferenceStore,
  private val analyticsManager: AnalyticsManager,
  private val crashReporter: CrashReporter,
) {
  private val _enabled = MutableStateFlow(store.load())
  val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

  init {
    apply(_enabled.value)
  }

  fun setEnabled(enabled: Boolean) {
    if (_enabled.value == enabled) return
    _enabled.value = enabled
    store.save(enabled)
    apply(enabled)
  }

  private fun apply(enabled: Boolean) {
    analyticsManager.setAnalyticsCollectionEnabled(enabled)
    crashReporter.setCrashCollectionEnabled(enabled)
  }
}
