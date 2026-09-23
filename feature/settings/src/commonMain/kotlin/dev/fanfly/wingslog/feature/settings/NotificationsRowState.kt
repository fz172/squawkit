package dev.fanfly.wingslog.feature.settings

/**
 * What the Notifications row's live subtitle should say (design §9.1). Computed once in the
 * ViewModel from [dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission] and
 * [dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager] rather than
 * exposing either raw stream to the view, so the precedence between "blocked at the OS level" and
 * "the user turned it off in-app" lives in one place. `BLOCKED` wins over `OFF`: fixing an OS-level
 * block is the more actionable thing to surface, and it is true regardless of the in-app toggle.
 */
enum class NotificationsRowState {
  /** Permission undetermined/granted and the master switch is on — the everyday case. */
  DEFAULT,

  /** Permission is fine; the user's own master switch is off. */
  OFF,

  /** OS-level permission is denied or the platform cannot show notifications at all. */
  BLOCKED,
}
