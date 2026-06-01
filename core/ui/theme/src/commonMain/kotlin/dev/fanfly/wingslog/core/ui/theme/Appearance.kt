package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the app picks between the light and dark color schemes.
 *
 * Persisted device-locally (not synced per account) because it is a per-device choice — see the
 * "on this device" copy in the Appearance setting — and because the theme wraps the whole app,
 * including the pre-login screens where no synced user-scoped storage is available yet.
 */
enum class AppearanceMode {
  LIGHT,
  SYSTEM,
  DARK,
}

/** Device-local persistence for the chosen [AppearanceMode]; implemented per platform. */
interface AppearanceStore {
  fun load(): AppearanceMode
  fun save(mode: AppearanceMode)
}

/**
 * Holds the active [AppearanceMode] as observable state, seeded from [AppearanceStore] and written
 * back on every change. Registered as a singleton so the root theme and the Settings screen share a
 * single source of truth and the theme updates the instant the user flips the toggle.
 */
class AppearanceController(private val store: AppearanceStore) {
  private val _mode = MutableStateFlow(store.load())
  val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

  fun setMode(mode: AppearanceMode) {
    if (_mode.value == mode) return
    _mode.value = mode
    store.save(mode)
  }
}

/** Resolves a mode to whether the dark scheme should apply, deferring to the OS for [SYSTEM]. */
@Composable
fun AppearanceMode.resolveDarkTheme(): Boolean = when (this) {
  AppearanceMode.LIGHT -> false
  AppearanceMode.DARK -> true
  AppearanceMode.SYSTEM -> isSystemInDarkTheme()
}
