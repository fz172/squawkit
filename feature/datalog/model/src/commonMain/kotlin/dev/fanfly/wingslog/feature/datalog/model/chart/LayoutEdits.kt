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
fun ChartLayout.kindOf(pane: PaneId, catalogue: Map<Int, DataLogSeries>): PaneKind? =
  panes.firstOrNull { it.id == pane }?.series?.firstOrNull()?.let { catalogue[it.column]?.paneKind() }

/** The next pane id: one above the highest in use, so a removed pane's id is never reused mid-session. */
fun ChartLayout.nextPaneId(): PaneId = PaneId((panes.maxOfOrNull { it.id.value } ?: -1) + 1)

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

  /** A new pane at the bottom holding [key] (or empty), which becomes the target. */
  fun spawn(layout: ChartLayout, key: SeriesKey? = null): ChartLayout {
    val id = layout.nextPaneId()
    return layout.copy(panes = layout.panes + Pane(id, listOfNotNull(key)), targetPane = id)
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
    val seriesKind = catalogue[key.column]?.paneKind() ?: PaneKind.CHART
    val targetKind = layout.kindOf(to, catalogue)
    val without = remove(layout, from, key)
    return if (targetKind != null && targetKind != seriesKind) spawn(without, key) else add(without, to, key)
  }

  /** Drops a pane; the target moves to the nearest remaining pane, or clears. */
  fun removePane(layout: ChartLayout, pane: PaneId): ChartLayout {
    val index = layout.panes.indexOfFirst { it.id == pane }
    if (index < 0) return layout
    val remaining = layout.panes.filterNot { it.id == pane }
    val target = if (layout.targetPane == pane) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id
    else layout.targetPane
    return layout.copy(panes = remaining, targetPane = target)
  }

  fun target(layout: ChartLayout, pane: PaneId): ChartLayout =
    if (layout.panes.any { it.id == pane }) layout.copy(targetPane = pane) else layout
}
