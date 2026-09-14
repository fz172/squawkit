package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/**
 * Eight colours per theme, chosen for contrast on that theme's surface (design §11.3, PRD R24a).
 * The index depends only on the series, never on pane position: a fixed table covers the common
 * engine, fuel, flight and electrical ids so the series a preset puts together never collide, and
 * everything else hashes. Colour changes only with the theme.
 */
object SeriesPalette {

  val DARK: List<Color> = listOf(
    Color(0xFFA7C8FF), Color(0xFFFFBA4E), Color(0xFF81C784), Color(0xFFFF8A80),
    Color(0xFF4DD0E1), Color(0xFFCE93D8), Color(0xFFFFCA28), Color(0xFFBAC8E0),
  )

  val LIGHT: List<Color> = listOf(
    Color(0xFF1A5FAE), Color(0xFF7A5200), Color(0xFF276B39), Color(0xFFB3261E),
    Color(0xFF00696F), Color(0xFF6A3F9D), Color(0xFF8B5E00), Color(0xFF525E72),
  )

  /** Indices for the ids a preset co-plots, distinct within each preset (Engine, Fuel, Flight, Electrical). */
  val FIXED_INDEX: Map<String, Int> = mapOf(
    CanonicalSeries.engine(1, "rpm") to 1,
    CanonicalSeries.engine(1, "map") to 0,
    CanonicalSeries.engine(1, "oil_press") to 2,
    CanonicalSeries.engine(1, "oil_temp") to 3,
    CanonicalSeries.engine(1, "fuel_flow") to 4,
    CanonicalSeries.engine(1, "fuel_press") to 5,
    CanonicalSeries.engine(1, "power_pct") to 6,
    CanonicalSeries.engine(1, "cht", 1) to 0,
    CanonicalSeries.engine(1, "cht", 2) to 1,
    CanonicalSeries.engine(1, "cht", 3) to 2,
    CanonicalSeries.engine(1, "cht", 4) to 3,
    CanonicalSeries.engine(1, "egt", 1) to 4,
    CanonicalSeries.engine(1, "egt", 2) to 5,
    CanonicalSeries.engine(1, "egt", 3) to 6,
    CanonicalSeries.engine(1, "egt", 4) to 7,
    CanonicalSeries.fuelQty(1) to 2,
    CanonicalSeries.fuelQty(2) to 6,
    CanonicalSeries.ALT_GPS to 0,
    CanonicalSeries.ALT_PRESSURE to 7,
    CanonicalSeries.ALT_BARO to 5,
    CanonicalSeries.IAS to 1,
    CanonicalSeries.TAS to 6,
    CanonicalSeries.VERTICAL_SPEED to 4,
    CanonicalSeries.GROUND_SPEED to 2,
    CanonicalSeries.AGL to 3,
    CanonicalSeries.volts(1) to 0,
    CanonicalSeries.volts(2) to 5,
    CanonicalSeries.amps(1) to 1,
    CanonicalSeries.amps(2) to 6,
    CanonicalSeries.OAT to 4,
    CanonicalSeries.HEADING to 7,
    CanonicalSeries.POSITION to 0,
  )

  fun colorFor(key: SeriesKey, canonicalId: String, dark: Boolean): Color {
    val palette = if (dark) DARK else LIGHT
    return palette[index(key, canonicalId)]
  }

  /** The fixed table, else FNV-1a over the canonical id (or the column when unmapped) modulo eight. */
  fun index(key: SeriesKey, canonicalId: String): Int {
    FIXED_INDEX[canonicalId]?.let { return it }
    val seed = canonicalId.ifEmpty { "column:${key.column}" }
    var hash = 0x811C9DC5.toInt()
    for (ch in seed) {
      hash = hash xor ch.code
      hash *= 0x01000193
    }
    return (hash and 0x7FFFFFFF) % DARK.size
  }
}
