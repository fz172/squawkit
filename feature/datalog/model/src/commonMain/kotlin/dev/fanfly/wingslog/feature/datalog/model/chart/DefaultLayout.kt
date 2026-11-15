package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/** True for anything the user can put in the layout: chart series (numeric, discrete, text), plus the map's position. */
val DataLogSeries.isSelectable: Boolean
  get() = isPlottable ||
    kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION ||
    kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT

/** True for the kinds a chart pane can draw as numeric/discrete curves. */
val DataLogSeries.isPlottable: Boolean
  get() = kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC ||
    kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE

/**
 * True for series that render as Gantt swimlanes underneath the numeric plot (Option 6) rather
 * than continuous Y-axis curves: `DISCRETE`, `TEXT`, and radio/nav frequencies (`MHz`).
 */
val DataLogSeries.isSwimlaneSeries: Boolean
  get() = kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE ||
    kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_TEXT ||
    unit.equals("MHz", ignoreCase = true)

private val MultiEngineShortName = Regex("""^E(?:[2-9]|\d{2,}) .+""")

/**
 * True when the log carries series for more than one engine — either by canonical id
 * (`engine[2].*`) or by a Garmin `E2 <field>` short name.
 */
fun List<DataLogSeries>.isMultiEngine(): Boolean = any { series ->
  (series.canonical_id.startsWith("engine[") && !series.canonical_id.startsWith(
    "engine[1]."
  )) ||
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
 * True when the series carries non-zero / non-empty data over the log. Numeric and discrete
 * columns that are null (`sample_count == 0`) or `0` the entire time (`min == 0.0 && max == 0.0`)
 * are treated as having no data so they can be greyed out in the sidebar and skipped for preview.
 */
val DataLogSeries.hasData: Boolean
  get() = when {
    sample_count <= 0 -> false
    kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC ||
      kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_DISCRETE ->
      min != 0.0 || max != 0.0

    else -> true
  }

/**
 * Ordered canonical IDs for picking the initial / preview chart series when RPM is absent or has
 * no non-zero data (e.g., ground / avionics-only logs).
 */
private val PreviewSeriesPriority: List<String> = listOf(
  CanonicalSeries.engine(1, "rpm"),
  CanonicalSeries.IAS,
  CanonicalSeries.GROUND_SPEED,
  CanonicalSeries.engine(1, "map"),
  CanonicalSeries.engine(1, "fuel_flow"),
  CanonicalSeries.amps(1),
  CanonicalSeries.volts(1),
  CanonicalSeries.volts(2),
  CanonicalSeries.engine(1, "oil_temp"),
  CanonicalSeries.engine(1, "coolant_temp"),
  CanonicalSeries.engine(1, "oil_press"),
  CanonicalSeries.engine(1, "egt", 1),
  CanonicalSeries.engine(1, "cht", 1),
  CanonicalSeries.ALT_BARO,
  CanonicalSeries.ALT_GPS,
  CanonicalSeries.ALT_PRESSURE,
)

/**
 * The layout a log opens with (PRD R21): a map pane when `POSITION` has GPS fixes (`hasData`),
 * otherwise one chart pane holding the highest-priority plottable series with non-zero data (RPM
 * first, then airspeed, MAP, fuel flow, amps, volts, oil/coolant temp, etc.), or the first
 * plottable series with data when none match; no pane at all for a log with nothing to show.
 */
fun defaultLayout(catalogue: List<DataLogSeries>): ChartLayout {
  val position = catalogue.firstOrNull {
    it.kind == DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION && it.hasData
  }
  if (position != null) {
    val pane = Pane(PaneId(0), listOf(SeriesKey(position.column)))
    return ChartLayout(panes = listOf(pane), targetPane = pane.id)
  }

  val plottable = catalogue.filter { it.isPlottable }
  if (plottable.isEmpty()) return ChartLayout.EMPTY

  // Prefer continuous numeric series with actual non-zero data; fall back to any plottable
  // series with data, and finally to any plottable series if all columns are 0/empty.
  val pool = plottable.filter { !it.isSwimlaneSeries && it.hasData }
    .ifEmpty { plottable.filter { it.hasData } }
    .ifEmpty { plottable }

  val first = PreviewSeriesPriority
    .firstNotNullOfOrNull { id -> pool.firstOrNull { it.canonical_id == id } }
    ?: pool.first()
  val pane = Pane(PaneId(0), listOf(SeriesKey(first.column)))
  return ChartLayout(panes = listOf(pane), targetPane = pane.id)
}
