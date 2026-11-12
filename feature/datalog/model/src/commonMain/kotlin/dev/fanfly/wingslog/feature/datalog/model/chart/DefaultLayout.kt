package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/** True for anything the user can put in the layout: a chart pane's kinds, plus the map's position. */
val DataLogSeries.isSelectable: Boolean
  get() = isPlottable || kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION

/** True for the kinds a chart pane can draw. */
val DataLogSeries.isPlottable: Boolean
  get() = kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC ||
    kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE

private val MultiEngineShortName = Regex("""^E(?:[2-9]|\d{2,}) .+""")

/**
 * True when the log carries series for more than one engine — either by canonical id
 * (`engine[2].*`) or by a Garmin `E2 <field>` short name.
 */
fun List<DataLogSeries>.isMultiEngine(): Boolean = any { series ->
  (series.canonical_id.startsWith("engine[") && !series.canonical_id.startsWith("engine[1].")) ||
    MultiEngineShortName.matches(series.short_name)
}

/**
 * The label a series chip displays: `short_name` (falling back to `name`), with Garmin's `"E1 "`
 * prefix stripped on a single-engine aircraft so `E1 RPM` reads as `RPM`. Avidyne's bare `E1`
 * (cylinder 1 EGT, no trailing space) is untouched.
 */
fun DataLogSeries.chipLabel(multiEngine: Boolean): String {
  val raw = short_name.ifBlank { name }
  return if (multiEngine) raw else raw.removePrefix("E1 ")
}

/**
 * The layout a log opens with (PRD R21): one pane holding the default series, RPM on the airplane
 * preset, or the first plottable series when the log has none; no pane at all for a log with
 * nothing to plot. T39 replaces the hard-wired canonical id with the template's presets.
 */
fun defaultLayout(catalogue: List<DataLogSeries>): ChartLayout {
  val rpm = catalogue.firstOrNull {
    it.canonical_id == CanonicalSeries.engine(
      1,
      "rpm"
    ) && it.isPlottable
  }
  val first =
    rpm ?: catalogue.firstOrNull { it.isPlottable } ?: return ChartLayout.EMPTY
  val pane = Pane(PaneId(0), listOf(SeriesKey(first.column)))
  return ChartLayout(panes = listOf(pane), targetPane = pane.id)
}
