package dev.fanfly.wingslog.feature.notifications.engine

/**
 * The four buckets a crossing batches into — at most one notification per (aircraft, tier) per
 * scan (design §6.5). Matches the four toggles in `NotificationSettingsExt` one-to-one.
 */
enum class UrgencyTier {
  /** A squawk reached `SQUAWK_PRIORITY_AOG`. */
  GROUNDED,

  /** An open squawk's priority increased, short of AOG. */
  PRIORITY_RAISED,

  /** A task crossed into `DueStatus.OVERDUE`. */
  OVERDUE,

  /** A task crossed into `DueStatus.DUE_SOON`. */
  DUE_SOON,
}

/** Enum trigger for [UrgencyScanner.scan] — only [MANUAL] has a caller today (design §6.6's other two land with P2.6-P2.8). */
enum class ScanTrigger {
  MANUAL,
  SCHEDULED,
  SESSION_BOUNDARY,
}

/** Every early-exit the scan can take, plus the terminal success case (design §6.3). */
sealed interface ScanResult {
  data object NoUser : ScanResult
  data object PrefsUnresolved : ScanResult
  data object Disabled : ScanResult
  data object NoPermission : ScanResult
  data class Completed(val notificationsPosted: Int) : ScanResult
}
