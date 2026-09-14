package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import org.junit.Test

class SeriesPaletteTest {

  /** Material 3's baseline surfaces, which the app's schemes leave at their defaults. */
  private val lightSurface = Color(0xFFFEF7FF)
  private val darkSurface = Color(0xFF141218)

  /** WCAG 2 contrast ratio. */
  private fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
  }

  @Test
  fun everyColourClearsThreeToOneOnItsSurface() {
    SeriesPalette.LIGHT.forEachIndexed { i, c ->
      assertWithMessage("light[$i]").that(contrast(c, lightSurface)).isAtLeast(3.0)
    }
    SeriesPalette.DARK.forEachIndexed { i, c ->
      assertWithMessage("dark[$i]").that(contrast(c, darkSurface)).isAtLeast(3.0)
    }
  }

  @Test
  fun presetSeriesNeverCollideWithinTheirPreset() {
    fun indices(vararg ids: String) = ids.map { SeriesPalette.index(SeriesKey(0), it) }
    val engine = indices(
      CanonicalSeries.engine(1, "rpm"), CanonicalSeries.engine(1, "map"),
      CanonicalSeries.engine(1, "oil_press"), CanonicalSeries.engine(1, "oil_temp"),
    )
    val cylinders = indices(*(1..4).map { CanonicalSeries.engine(1, "cht", it) }.toTypedArray() +
      (1..4).map { CanonicalSeries.engine(1, "egt", it) }.toTypedArray())
    val flight = indices(CanonicalSeries.ALT_GPS, CanonicalSeries.IAS, CanonicalSeries.VERTICAL_SPEED)
    val electrical = indices(CanonicalSeries.volts(1), CanonicalSeries.volts(2), CanonicalSeries.amps(1), CanonicalSeries.amps(2))
    listOf(engine, cylinders, flight, electrical).forEach { assertThat(it.toSet()).hasSize(it.size) }
  }

  @Test
  fun colourDependsOnlyOnTheSeriesAndTheme() {
    val rpm = CanonicalSeries.engine(1, "rpm")
    // Same id, any column, any call: the same colour; the theme is the only other input.
    assertThat(SeriesPalette.colorFor(SeriesKey(81), rpm, dark = true))
      .isEqualTo(SeriesPalette.colorFor(SeriesKey(3), rpm, dark = true))
    assertThat(SeriesPalette.colorFor(SeriesKey(81), rpm, dark = true))
      .isNotEqualTo(SeriesPalette.colorFor(SeriesKey(81), rpm, dark = false))
    assertThat(SeriesPalette.index(SeriesKey(81), rpm)).isEqualTo(1)
    // Unmapped columns hash deterministically and stay within the palette.
    val a = SeriesPalette.index(SeriesKey(55), "")
    assertThat(a).isEqualTo(SeriesPalette.index(SeriesKey(55), ""))
    assertThat(a).isIn(0 until 8)
    assertThat(SeriesPalette.index(SeriesKey(55), "")).isNotEqualTo(SeriesPalette.index(SeriesKey(56), "").takeIf { it != a } ?: -1)
  }
}
