package dev.fanfly.wingslog.feature.notifications.model

import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import kotlin.jvm.JvmInline

/**
 * Urgency rank within one ladder. NOT the enum ordinal — `DueStatus.COMPLIED` has the HIGHEST
 * ordinal (`NORMAL, DUE_SOON, OVERDUE, COMPLIED`) and the LOWEST urgency. Two records are only ever
 * comparable within the same ladder, which is why the watermark key carries the `CollectionKind`
 * (design §6.2).
 */
@JvmInline
value class UrgencyRank(val value: Int) : Comparable<UrgencyRank> {
  override fun compareTo(other: UrgencyRank) = value.compareTo(other.value)
  companion object {
    val RESOLVED = UrgencyRank(0)
  }
}

// Exhaustive `when`, no `else`: a new DueStatus value must fail the build here rather than silently
// ranking 0 and going unreported forever (design §6.1).
fun DueStatus.urgencyRank(): UrgencyRank = when (this) {
  DueStatus.COMPLIED, DueStatus.NORMAL -> UrgencyRank(0)
  DueStatus.DUE_SOON -> UrgencyRank(1)
  DueStatus.OVERDUE -> UrgencyRank(2)
}

// Reopen needs no special case, and that is the check that this mapping is right: DISMISSED/
// ADDRESSED is rank 0, reopening restores OPEN at the stored priority, so rank goes 0 -> 1..4 and
// the plain `rank > watermark` test fires on its own. An open squawk is never rank 0 — UNKNOWN
// priority (squawks written before priority was mandatory) maps to 1, not 0, since an unset
// priority is still an open defect.
fun SquawkWithStatus.urgencyRank(): UrgencyRank = when (status) {
  SquawkStatus.ADDRESSED, SquawkStatus.DISMISSED -> UrgencyRank(0)
  SquawkStatus.OPEN -> when (squawk.priority) {
    SquawkPriority.SQUAWK_PRIORITY_AOG -> UrgencyRank(4)
    SquawkPriority.SQUAWK_PRIORITY_HIGH -> UrgencyRank(3)
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> UrgencyRank(2)
    SquawkPriority.SQUAWK_PRIORITY_LOW,
    SquawkPriority.SQUAWK_PRIORITY_UNKNOWN -> UrgencyRank(1)
  }
}
