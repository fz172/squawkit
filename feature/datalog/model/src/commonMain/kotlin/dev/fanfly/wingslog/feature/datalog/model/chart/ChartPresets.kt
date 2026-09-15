package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.Pane
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/**
 * One-tap layouts (PRD R30, design §11.8): pane lists of canonical ids, resolved against a
 * record's catalogue at open time so a series the log lacks is skipped silently. Airplane-only in
 * V1; a template-declared list can replace this when a second domain records data.
 */
enum class ChartPreset(val panes: List<List<String>>) {
  ENGINE(
    listOf(
      listOf(CanonicalSeries.engine(1, "rpm"), CanonicalSeries.engine(1, "map")),
      listOf(CanonicalSeries.engine(1, "oil_press"), CanonicalSeries.engine(1, "oil_temp")),
      (1..6).map { CanonicalSeries.engine(1, "cht", it) },
      (1..6).map { CanonicalSeries.engine(1, "egt", it) },
    )
  ),
  FUEL(
    listOf(
      listOf(CanonicalSeries.engine(1, "fuel_flow"), CanonicalSeries.engine(1, "fuel_press")),
      (1..2).map { CanonicalSeries.fuelQty(it) },
    )
  ),
  FLIGHT(
    listOf(
      listOf(CanonicalSeries.ALT_GPS, CanonicalSeries.ALT_BARO, CanonicalSeries.ALT_PRESSURE),
      listOf(CanonicalSeries.IAS, CanonicalSeries.TAS, CanonicalSeries.GROUND_SPEED),
      listOf(CanonicalSeries.VERTICAL_SPEED),
    )
  ),
  ELECTRICAL(
    listOf(
      (1..2).map { CanonicalSeries.volts(it) },
      (1..2).map { CanonicalSeries.amps(it) },
    )
  );

  /** The preset as panes of this catalogue's columns; null when the log carries none of its series. */
  fun resolve(catalogue: List<DataLogSeries>): ChartLayout? {
    val byId = catalogue.filter { it.isPlottable && it.canonical_id.isNotBlank() }.associateBy { it.canonical_id }
    val panes = panes
      .map { ids -> ids.mapNotNull { byId[it] }.map { SeriesKey(it.column) } }
      .filter { it.isNotEmpty() }
      .mapIndexed { index, keys -> Pane(PaneId(index), keys) }
    if (panes.isEmpty()) return null
    return ChartLayout(panes = panes, targetPane = panes.first().id)
  }
}
