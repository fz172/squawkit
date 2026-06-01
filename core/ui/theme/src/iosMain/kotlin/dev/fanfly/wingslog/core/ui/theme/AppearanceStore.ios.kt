package dev.fanfly.wingslog.core.ui.theme

import platform.Foundation.NSUserDefaults

/** Device-local appearance preference backed by [NSUserDefaults]. */
class IosAppearanceStore : AppearanceStore {
  private val defaults = NSUserDefaults.standardUserDefaults

  override fun load(): AppearanceMode =
    defaults.stringForKey(KEY)
      ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
      ?: AppearanceMode.SYSTEM

  override fun save(mode: AppearanceMode) {
    defaults.setObject(mode.name, KEY)
  }

  private companion object {
    const val KEY = "appearance_mode"
  }
}
