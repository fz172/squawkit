package dev.fanfly.wingslog.feature.logs.dashboard

import dev.fanfly.wingslog.thing.MaintenanceLog

/**
 * The Overview tab's log slice: meter reading and count stats plus the 4 most recent logs.
 */
data class LogOverviewUiState(
  val loaded: Boolean = false,
  val logStats: LogStats? = null,
  val recentLogs: List<MaintenanceLog> = emptyList(),
)
