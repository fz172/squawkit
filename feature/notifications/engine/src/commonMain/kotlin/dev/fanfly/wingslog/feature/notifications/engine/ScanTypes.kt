package dev.fanfly.wingslog.feature.notifications.engine

import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger

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
