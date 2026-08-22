package dev.fanfly.wingslog.feature.notifications.engine

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
