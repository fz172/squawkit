package dev.fanfly.wingslog.core.analytics

import android.content.Context

/** Device-local Firebase Logging preference backed by [android.content.SharedPreferences]. */
class AndroidAnalyticsPreferenceStore(context: Context) : AnalyticsPreferenceStore {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(): Boolean =
    prefs.getBoolean(KEY, true)

  override fun save(enabled: Boolean) {
    prefs.edit()
      .putBoolean(KEY, enabled)
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "analytics_prefs"
    const val KEY = "firebase_logging_enabled"
  }
}
