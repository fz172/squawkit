package dev.fanfly.wingslog.core.ui.theme

import android.content.Context

/** Device-local appearance preference backed by [android.content.SharedPreferences]. */
class AndroidAppearanceStore(context: Context) : AppearanceStore {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(): AppearanceMode =
    prefs.getString(KEY, null)
      ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
      ?: AppearanceMode.SYSTEM

  override fun save(mode: AppearanceMode) {
    prefs.edit()
      .putString(KEY, mode.name)
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "appearance_prefs"
    const val KEY = "appearance_mode"
  }
}
