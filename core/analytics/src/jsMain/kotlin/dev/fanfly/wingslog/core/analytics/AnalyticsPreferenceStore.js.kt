package dev.fanfly.wingslog.core.analytics

import kotlinx.browser.localStorage

/** Device-local Firebase Logging preference backed by the browser's `localStorage`. */
class JsAnalyticsPreferenceStore : AnalyticsPreferenceStore {
  override fun load(): Boolean =
    localStorage.getItem(KEY)?.toBooleanStrictOrNull() ?: true

  override fun save(enabled: Boolean) {
    localStorage.setItem(KEY, enabled.toString())
  }

  private companion object {
    const val KEY = "firebase_logging_enabled"
  }
}
