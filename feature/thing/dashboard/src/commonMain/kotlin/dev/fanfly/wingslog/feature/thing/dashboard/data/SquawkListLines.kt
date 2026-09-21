package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.feature.ads.model.ListRow
import dev.fanfly.wingslog.feature.ads.model.withAdSlotsGrouped
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.thing.SquawkPriority

/** One lazy item of the squawk list. [key] is unique in the list and stable. */
sealed interface SquawkListLine {
  val key: String

  /** Names a priority tier and says how many squawks sit under it. */
  data class TierHeader(val tier: SquawkPriority, val count: Int) : SquawkListLine {
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

/** Most urgent first — the order the tiers are listed in. */
val SQUAWK_TIERS = listOf(
  SquawkPriority.SQUAWK_PRIORITY_AOG,
  SquawkPriority.SQUAWK_PRIORITY_HIGH,
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
  SquawkPriority.SQUAWK_PRIORITY_LOW,
)

/** An unset priority reads as Low everywhere else, so it groups there too. */
val SquawkWithStatus.tier: SquawkPriority
  get() = squawk.priority.takeIf { it in SQUAWK_TIERS } ?: SquawkPriority.SQUAWK_PRIORITY_LOW

/**
 * Flattens [squawks] into the lines a `LazyColumn` renders. [grouped] puts each tier under its
 * header and keeps the incoming order within it; empty tiers get no header. A header or an ad
 * always ends the line of the grid before it.
 */
fun squawkListLines(
  squawks: List<SquawkWithStatus>,
  grouped: Boolean,
  columns: Int,
  showAds: Boolean,
): List<SquawkListLine> {
  val entries: List<SquawkListLine> = if (grouped) {
    SQUAWK_TIERS.flatMap { tier ->
      val inTier = squawks.filter { it.tier == tier }
      if (inTier.isEmpty()) {
        emptyList()
      } else {
        listOf(SquawkListLine.TierHeader(tier, inTier.size)) +
          inTier.map { SquawkListLine.Records(listOf(it)) }
      }
    }
  } else {
    squawks.map { SquawkListLine.Records(listOf(it)) }
  }
  val rows = if (showAds) {
    withAdSlotsGrouped(entries) { it is SquawkListLine.TierHeader }
  } else {
    entries.map { ListRow.Item(it) }
  }

  val perLine = columns.coerceAtLeast(1)
  return buildList {
    val run = ArrayList<SquawkWithStatus>(perLine)
    fun flush() {
      if (run.isNotEmpty()) add(SquawkListLine.Records(run.toList()))
      run.clear()
    }
    rows.forEach { row ->
      val entry = when (row) {
        is ListRow.Ad -> SquawkListLine.Ad(row.slotIndex)
        is ListRow.Item -> row.value
      }
      if (entry is SquawkListLine.Records) {
        run += entry.items
        if (run.size == perLine) flush()
      } else {
        flush()
        add(entry)
      }
    }
    flush()
  }
}
