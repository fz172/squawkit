package dev.fanfly.wingslog.feature.squawk.model

import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.fanfly.wingslog.thing.SquawkPriority

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

/**
 * The AOG squawks that are still open — the ones the dashboard alert is about.
 *
 * A squawk closes two ways: addressed by a maintenance log, or dismissed with a reason. Callers
 * that re-derive "open" from [Squawk.addressed_by_log_id] alone keep dismissed AOG squawks on the
 * banner after they have been closed.
 */
fun List<SquawkWithStatus>.openAog(): List<Squawk> =
  filter {
    it.squawk.priority == SquawkPriority.SQUAWK_PRIORITY_AOG && it.status == SquawkStatus.OPEN
  }.map { it.squawk }
