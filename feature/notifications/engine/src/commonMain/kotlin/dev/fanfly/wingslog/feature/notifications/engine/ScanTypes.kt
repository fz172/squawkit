package dev.fanfly.wingslog.feature.notifications.engine

/**
 * Enum trigger for [UrgencyScanner.scan]. Only [SESSION_BOUNDARY] is debounced against
 * [LastScanStore] — an app opened six times an hour should not scan six times, while the periodic
 * background job is already on the cadence the debounce would enforce (design §6.6).
 */
enum class ScanTrigger {
  MANUAL,
  SCHEDULED,
  SESSION_BOUNDARY,
}

/** Every early-exit the scan can take, plus the terminal success case (design §6.3). */
sealed interface ScanResult {
  data object NoUser : ScanResult

  /** A [ScanTrigger.SESSION_BOUNDARY] scan that arrived inside the debounce window (design §6.6). */
  data object Debounced : ScanResult
  data object PrefsUnresolved : ScanResult
  data object Disabled : ScanResult
  data object NoPermission : ScanResult
  data class Completed(val notificationsPosted: Int) : ScanResult
}
