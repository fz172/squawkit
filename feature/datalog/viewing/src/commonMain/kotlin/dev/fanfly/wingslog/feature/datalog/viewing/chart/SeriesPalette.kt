package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/**
 * Eight colours per theme, chosen for contrast on that theme's surface (design §11.3). The index
 * depends only on the series, never on pane position, so a series keeps its colour across moves,
 * removals and reopens (PRD R24a). T31 adds the fixed-index table for the common ids; until then
 * every series hashes.
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

  fun colorFor(key: SeriesKey, canonicalId: String, dark: Boolean): Color {
    val palette = if (dark) DARK else LIGHT
    return palette[index(key, canonicalId)]
  }

  /** FNV-1a over the canonical id when there is one, else over the column, modulo the palette. */
  fun index(key: SeriesKey, canonicalId: String): Int {
    val seed = canonicalId.ifEmpty { "column:${key.column}" }
    var hash = 0x811C9DC5.toInt()
    for (ch in seed) {
      hash = hash xor ch.code
      hash *= 0x01000193
    }
    return (hash and 0x7FFFFFFF) % DARK.size
  }
}
