package dev.fanfly.wingslog.feature.notifications.engine

/** Enum trigger for [UrgencyScanner.scan] — [SESSION_BOUNDARY] is still uncalled (design §6.6, P2.8). */
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
