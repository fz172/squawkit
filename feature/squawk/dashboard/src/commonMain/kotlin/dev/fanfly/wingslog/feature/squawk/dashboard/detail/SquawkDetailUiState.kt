package dev.fanfly.wingslog.feature.squawk.dashboard.detail

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.thing.MaintenanceLog

data class SquawkDetailUiState(
  val selectedSquawk: SquawkWithStatus? = null,
  val logForSelectedSquawk: MaintenanceLog? = null,
  /** Resolve bubble open for this squawk (design §5.1). */
  val resolvingSquawkId: SquawkId? = null,
  /** Dismiss-reason dialog open for this squawk. */
  val dismissingSquawkId: SquawkId? = null,
  /** Delete confirmation open for this squawk. */
  val deletingSquawkId: SquawkId? = null,
)
