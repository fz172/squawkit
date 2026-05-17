package dev.fanfly.wingslog.feature.squawk.model

import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason

data class SquawkWithStatus(
  val squawk: Squawk,
  val status: SquawkStatus,
)

fun Squawk.toWithStatus(): SquawkWithStatus = SquawkWithStatus(
  squawk = this,
  status = when {
    addressed_by_log_id.isNotEmpty() -> SquawkStatus.ADDRESSED
    dismiss_reason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN -> SquawkStatus.DISMISSED
    else -> SquawkStatus.OPEN
  },
)
