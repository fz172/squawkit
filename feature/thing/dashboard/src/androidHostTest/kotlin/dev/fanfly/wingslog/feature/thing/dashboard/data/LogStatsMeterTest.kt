package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import org.junit.Test

/**
 * Which meters the dashboard summary can put a number against (#703).
 *
 * The card renders one cell per meter the template declares. What it can *fill* is a different
 * question, and this is it: a log carries the three aviation hour fields and nothing else, so every
 * non-aviation meter is declared-but-unrecorded until #730 stores readings per meter key.
 */
class LogStatsMeterTest {

  private val stats = LogStats(
    total = 12,
    airframe = 4,
    engine = 5,
    propeller = 3,
    currentAirframeTime = 1111.0,
    currentEngineTime = 1041.8,
    currentPropTime = 1029.8,
  )

  @Test
  fun theThreeAviationMetersResolveToTheirStoredReadings() {
    assertThat(stats.valueFor(MeterKeys.AIRFRAME_HOURS)).isEqualTo(1111.0)
    assertThat(stats.valueFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1041.8)
    assertThat(stats.valueFor(MeterKeys.PROP_HOURS)).isEqualTo(1029.8)
  }

  @Test
  fun anOdometerHasNoStoredReadingYet() {
    // Null, not 0.0 — the card shows an em dash for this rather than inventing a mileage. The cell
    // still renders, because a car's odometer existing and being unlogged is worth saying.
    assertThat(stats.valueFor("odometer")).isNull()
    assertThat(stats.valueFor("ride_hours")).isNull()
  }

  @Test
  fun everyMeterAnyPresetDeclaresIsEitherReadableOrKnownUnrecorded() {
    // Guards the mapping against a preset declaring a meter nobody thought about: the result is
    // always a reading or a deliberate null, never an exception or a wrong field.
    CanonicalTemplates.ALL.flatMap { it.meters }.forEach { meter ->
      val value = stats.valueFor(meter.key)
      if (meter.key in AVIATION_METERS) {
        assertThat(value).isNotNull()
      } else {
        assertThat(value).isNull()
      }
    }
  }

  @Test
  fun theTemplateDecidesWhetherAMeterTakesDecimals() {
    // Hours do, an odometer does not — "84512.0 mi" is not how anyone writes mileage.
    val automotive = CanonicalTemplates.AUTOMOTIVE.meters.single()
    assertThat(automotive.key).isEqualTo("odometer")
    assertThat(automotive.decimal).isFalse()

    CanonicalTemplates.ALL.flatMap { it.meters }
      .filter { it.unit_label == "hrs" }
      .forEach { assertThat(it.decimal).isTrue() }
  }

  private companion object {
    val AVIATION_METERS = setOf(
      MeterKeys.AIRFRAME_HOURS,
      MeterKeys.ENGINE_HOURS,
      MeterKeys.PROP_HOURS,
    )
  }
}
