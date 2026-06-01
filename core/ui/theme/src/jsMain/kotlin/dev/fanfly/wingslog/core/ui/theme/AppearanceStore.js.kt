package dev.fanfly.wingslog.core.ui.theme

import kotlinx.browser.localStorage

/** Device-local appearance preference backed by the browser's `localStorage`. */
class JsAppearanceStore : AppearanceStore {
  override fun load(): AppearanceMode =
    localStorage.getItem(KEY)
      ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
      ?: AppearanceMode.SYSTEM

  override fun save(mode: AppearanceMode) {
    localStorage.setItem(KEY, mode.name)
  }

  private companion object {
    const val KEY = "appearance_mode"
  }
}
