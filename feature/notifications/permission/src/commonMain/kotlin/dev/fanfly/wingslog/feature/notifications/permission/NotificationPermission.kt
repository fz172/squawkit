package dev.fanfly.wingslog.feature.notifications.permission

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether this device may show notifications, and the two actions available on that answer:
 * asking the OS, or sending the user to fix it themselves. See design §5.1.
 *
 * This is the whole module. Four consumers — the onboarding primer, the settings screen and its
 * banner, and the urgency scanner's precondition check — need exactly this and nothing else the
 * feature owns, which is why `:permission` depends on nothing beyond `core:lifecycle` /
 * `core:appinfo`: not `:model`, not `:viewing`, not a feature datamanager.
 */
interface NotificationPermission {

  /** Cheap, synchronous-ish read of the OS state. Re-read on foreground: the user can change it in system settings. */
  fun observe(): StateFlow<PermissionState>

  /** Re-reads the OS state and updates [observe]'s value. Call on foreground and before any decision that depends on the current state. */
  suspend fun refresh()

  /**
   * Shows the real OS dialog. No-ops (and reports the current state without prompting) when not
   * [PermissionState.UNDETERMINED] — including [PermissionState.UNSUPPORTED], where there is
   * nothing to prompt for.
   */
  suspend fun request(): PermissionState

  /** True where the platform exposes a deep link to its own app-settings page — false on web, where no such API exists. */
  val canOpenSystemSettings: Boolean

  /** Opens the platform's notification settings for this app. No-op where [canOpenSystemSettings] is false. */
  fun openSystemSettings()
}
