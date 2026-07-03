package dev.fanfly.wingslog.core.analytics

import platform.Foundation.NSUserDefaults

/** Device-local Firebase Logging preference backed by [NSUserDefaults]. */
class IosAnalyticsPreferenceStore : AnalyticsPreferenceStore {
  private val defaults = NSUserDefaults.standardUserDefaults

  override fun load(): Boolean =
    if (defaults.objectForKey(KEY) == null) true else defaults.boolForKey(KEY)

  override fun save(enabled: Boolean) {
    defaults.setBool(enabled, KEY)
  }

  private companion object {
    const val KEY = "firebase_logging_enabled"
  }
}
