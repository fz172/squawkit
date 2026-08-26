package dev.fanfly.wingslog.feature.notifications.permission

/**
 * The OS-level state of notification permission for this app/device. See design §5.1.
 */
enum class PermissionState {
  /** Never asked, or asked and still answerable — `request()` shows the real OS dialog. */
  UNDETERMINED,

  GRANTED,

  /** Refused, at the OS level. Fixable only through system settings ([NotificationPermission.openSystemSettings]). */
  DENIED,

  /**
   * The platform has no notification capability at all — distinct from [DENIED]. On web this means
   * the `Notification` API is absent (an insecure origin, or a browser old enough not to implement
   * it). Reporting [DENIED] here would tell the settings screen to offer an "Open settings" fix that
   * does not exist for a missing API; conflating "blocked" with "cannot exist here" sends a pilot
   * hunting through a page that will never solve it. Never occurs on Android or iOS, where the
   * permission surface is always present at `minSdk` 33.
   *
   * Deliberately not a fourth `AppCapability` flag — whether the API exists is a runtime property of
   * the running browser, not the build, and this interface already answers exactly that class of
   * question. See design §5.1, delta D15.
   */
  UNSUPPORTED,
}
