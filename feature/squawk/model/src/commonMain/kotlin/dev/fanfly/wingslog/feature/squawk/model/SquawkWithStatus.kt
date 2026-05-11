package dev.fanfly.wingslog.feature.squawk.model

import dev.fanfly.wingslog.aircraft.Squawk

data class SquawkWithStatus(
  val squawk: Squawk,
  val status: SquawkStatus,
)

fun Squawk.toWithStatus(): SquawkWithStatus = SquawkWithStatus(
  squawk = this,
  status = if (addressed_by_log_id.isEmpty()) SquawkStatus.OPEN else SquawkStatus.ADDRESSED,
)
