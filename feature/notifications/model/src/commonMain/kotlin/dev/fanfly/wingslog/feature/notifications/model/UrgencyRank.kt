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

/**
 * The three buckets a crossing batches into — at most one notification per (thing, tier) per scan
 * (design §6.5). Matches the three toggles in `NotificationSettingsExt` one-to-one.
 *
 * Deliberately a separate type from [UrgencyRank], not merged into it, even though both describe
 * "how urgent" — [UrgencyRank] is a raw per-ladder number that exists purely to be compared
 * (`rank > watermark`) and covers every rank on both ladders, including ranks nobody is ever
 * notified about (e.g. `MEDIUM` — see [SquawkWithStatus.reportableTier]). [UrgencyTier] is the
 * opposite shape: not comparable, only defined for the ranks that map to a real settings toggle, and
 * a many-to-one target (two squawk ranks and `RESOLVED` all funnel into `PRIORITY_RAISED` or
 * nothing). Their derivation lives together here regardless, next to the ranks a tier is derived
 * from.
 */
enum class UrgencyTier {
  /**
   * An open squawk's priority increased, including all the way to `SQUAWK_PRIORITY_AOG`. AOG is not
   * its own tier (design decision, 2026-08-26) — it reports exactly like any other priority raise.
   */
  PRIORITY_RAISED,

  /** A task crossed into `DueStatus.OVERDUE`. */
  OVERDUE,

  /** A task crossed into `DueStatus.DUE_SOON`. */
  DUE_SOON,
}

// Exhaustive `when`, no `else`, matching urgencyRank()'s own exhaustiveness rule above.
fun DueStatus.reportableTier(): UrgencyTier? = when (this) {
  DueStatus.OVERDUE -> UrgencyTier.OVERDUE
  DueStatus.DUE_SOON -> UrgencyTier.DUE_SOON
  DueStatus.NORMAL, DueStatus.COMPLIED -> null
}

/**
 * `null` covers both a resolved squawk and an open one below HIGH — MEDIUM/LOW/UNKNOWN still rank
 * above [UrgencyRank.RESOLVED] (see [urgencyRank] above) so the watermark advances correctly and a
 * later real escalation is still caught, but design §9.2's "becomes high priority or worse" scopes
 * the *notification* to HIGH and up only.
 */
fun SquawkWithStatus.reportableTier(): UrgencyTier? {
  if (status != SquawkStatus.OPEN) return null
  return when (squawk.priority) {
    SquawkPriority.SQUAWK_PRIORITY_AOG,
    SquawkPriority.SQUAWK_PRIORITY_HIGH -> UrgencyTier.PRIORITY_RAISED
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
    SquawkPriority.SQUAWK_PRIORITY_LOW,
    SquawkPriority.SQUAWK_PRIORITY_UNKNOWN -> null
  }
}
