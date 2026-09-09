package dev.fanfly.wingslog.feature.search.model

import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate

/**
 * How many records carry a filter option, for the number beside its chip.
 *
 * Counted against the **sub-view's own unfiltered list** — every open squawk, not the ones the
 * other chips have already left standing. That is the number a pilot is asking for when they look
 * at a chip before tapping it ("how many airframe logs are there"), and it is stable: counts that
 * moved every time another chip was tapped would be unreadable, and a zero would be ambiguous
 * between "none exist" and "none survive your other choices".
 */
fun <T> List<T>.countByComponent(
  adapter: RecordAdapter<T>,
  component: ComponentType,
): Int = count { adapter.component(it) == component }

/** The same, for a time window. Dateless records follow the adapter's [RecordAdapter.nullDateMatches]. */
fun <T> List<T>.countByTime(
  adapter: RecordAdapter<T>,
  window: TimeWindow,
  today: LocalDate,
): Int = count { item ->
  if (window == TimeWindow.All) return@count true
  val date = adapter.date(item) ?: return@count adapter.nullDateMatches
  window.contains(date, today, adapter.direction(item))
}
