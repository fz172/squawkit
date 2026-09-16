package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/** What a pane draws: numeric lines or a map (PRD R29). The two never share a pane. */
enum class PaneKind { CHART, MAP }

fun DataLogSeries.paneKind(): PaneKind =
  if (kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION) PaneKind.MAP else PaneKind.CHART

/** The kind of [pane] from its first series; an empty pane accepts either. */
fun ChartLayout.kindOf(
  pane: PaneId,
  catalogue: Map<Int, DataLogSeries>
): PaneKind? =
  panes.firstOrNull { it.id == pane }?.series?.firstOrNull()
    ?.let { catalogue[it.column]?.paneKind() }

/** The next pane id: one above the highest in use, so a removed pane's id is never reused mid-session. */
fun ChartLayout.nextPaneId(): PaneId =
  PaneId((panes.maxOfOrNull { it.id.value } ?: -1) + 1)

/**
 * Map panes lead, charts follow, and each group keeps the order it had. The map is the one pane
 * that answers "where", so it reads first; it is also the tallest, which puts the ragged edge at
 * the bottom of the stack rather than in the middle.
 */
fun ChartLayout.withMapFirst(catalogue: Map<Int, DataLogSeries>): ChartLayout {
  val ordered = panes.sortedBy { pane ->
    val kind = pane.series.firstOrNull()
      ?.let { catalogue[it.column]?.paneKind() }
    if (kind == PaneKind.MAP) 0 else 1
  }
  return if (ordered == panes) this else copy(panes = ordered)
}

/** Pure edits to a layout (design §11.1, §11.5). Every result keeps [ChartLayout.targetPane] valid. */
object LayoutEdits {

  /** Adds [key] to [pane]; a series already there is left alone. */
  fun add(layout: ChartLayout, pane: PaneId, key: SeriesKey): ChartLayout =
    layout.copy(
      panes = layout.panes.map { p ->
        if (p.id == pane && key !in p.series) p.copy(series = p.series + key) else p
      },
      targetPane = pane,
    )

  fun remove(layout: ChartLayout, pane: PaneId, key: SeriesKey): ChartLayout =
    layout.copy(panes = layout.panes.map { p -> if (p.id == pane) p.copy(series = p.series - key) else p })

  /**
   * Puts [key] where it belongs rather than where it was dropped: map and chart series never share
   * a pane (PRD R29), and the map gets exactly one pane, so a position series joins the map pane
   * that already exists and otherwise opens one.
   */
  fun place(
    layout: ChartLayout,
    pane: PaneId,
    key: SeriesKey,
    catalogue: Map<Int, DataLogSeries>,
  ): ChartLayout {
    val destination = layout.destinationFor(pane, key, catalogue)
    return if (destination == null) spawn(layout, key) else add(
      layout,
      destination,
      key
    )
  }

  /**
   * The pane [key] lands in when placed on [pane], or null when it needs one of its own. A map
   * series ignores [pane] entirely: it goes to the map pane wherever that is.
   */
  fun ChartLayout.destinationFor(
    pane: PaneId,
    key: SeriesKey,
    catalogue: Map<Int, DataLogSeries>,
  ): PaneId? {
    val seriesKind = catalogue[key.column]?.paneKind() ?: PaneKind.CHART
    if (seriesKind == PaneKind.MAP) return panes.firstOrNull {
      kindOf(
        it.id,
        catalogue
      ) == PaneKind.MAP
    }?.id
    return if (kindOf(pane, catalogue) == PaneKind.MAP) null else pane
  }

  /** A new pane at the bottom holding [key] (or empty), which becomes the target. */
  fun spawn(layout: ChartLayout, key: SeriesKey? = null): ChartLayout {
    val id = layout.nextPaneId()
    return layout.copy(
      panes = layout.panes + Pane(id, listOfNotNull(key)),
      targetPane = id
    )
  }

  /**
   * Moves [key] from one pane to another. A drop onto a pane whose kind differs from the series
   * (a map series onto a chart pane, or the reverse) spawns a new pane instead, matching the
   * mock's `placeKey` (design §11.5). Dropping onto the pane it came from changes nothing.
   */
  fun move(
    layout: ChartLayout,
    key: SeriesKey,
    from: PaneId,
    to: PaneId,
    catalogue: Map<Int, DataLogSeries>,
  ): ChartLayout {
    if (from == to) return layout
    // The position series has exactly one pane it can live in, so dragging it anywhere else is not
    // a move at all; without this it would leave the map pane behind and open a second one.
    if (catalogue[key.column]?.paneKind() == PaneKind.MAP) return layout
    return place(remove(layout, from, key), to, key, catalogue)
  }

  /** Drops a pane; the target moves to the nearest remaining pane, or clears. */
  fun removePane(layout: ChartLayout, pane: PaneId): ChartLayout {
    val index = layout.panes.indexOfFirst { it.id == pane }
    if (index < 0) return layout
    val remaining = layout.panes.filterNot { it.id == pane }
    val target = if (layout.targetPane == pane) remaining.getOrNull(
      index.coerceAtMost(remaining.lastIndex)
    )?.id
    else layout.targetPane
    return layout.copy(panes = remaining, targetPane = target)
  }

  fun target(layout: ChartLayout, pane: PaneId): ChartLayout =
    if (layout.panes.any { it.id == pane }) layout.copy(targetPane = pane) else layout
}
