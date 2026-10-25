package dev.fanfly.wingslog.feature.squawk.dashboard.detail

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.thing.MaintenanceLog

data class SquawkDetailUiState(
  val selectedSquawk: SquawkWithStatus? = null,
  val logForSelectedSquawk: MaintenanceLog? = null,
  /** Resolve bubble open for this squawk (design §5.1). */
  val resolvingSquawkId: String? = null,
  /** Dismiss-reason dialog open for this squawk. */
  val dismissingSquawkId: String? = null,
  /** Delete confirmation open for this squawk. */
  val deletingSquawkId: String? = null,
)
