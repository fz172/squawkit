package dev.fanfly.wingslog.feature.squawk.dashboard

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.thing.SquawkPriority

/** One lazy item of the squawk list. [key] is unique in the list and stable. */
sealed interface SquawkListLine {
  val key: String

  /** Names a priority tier and says how many squawks sit under it. */
  data class TierHeader(val tier: SquawkPriority, val count: Int) :
    SquawkListLine {
    override val key: String get() = "tier-${tier.name}"
  }

  /** One line of the grid: up to `columns` squawks, a single one on a phone. */
  data class Records(val items: List<SquawkWithStatus>) : SquawkListLine {
    override val key: String get() = items.first().squawk.id
  }

  data class Ad(val slotIndex: Int) : SquawkListLine {
    override val key: String get() = "ad-$slotIndex"
  }
}
