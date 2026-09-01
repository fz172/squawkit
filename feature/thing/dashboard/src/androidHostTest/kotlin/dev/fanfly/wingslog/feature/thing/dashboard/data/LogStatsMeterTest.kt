package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import org.junit.Test

/**
 * What the dashboard summary can put a number against (#730).
 *
 * The card draws one cell per meter the template declares; this is what fills them. It used to map
 * three aviation fields by name, so a car's odometer had no answer to give however the template
 * asked — the readings are keyed now, and a meter is either recorded or absent.
 */
class LogStatsMeterTest {

  private val stats = LogStats(
    total = 12,
    airframe = 4,
    engine = 5,
    propeller = 3,
    readings = mapOf(
      MeterKeys.AIRFRAME_HOURS to 1111.0,
      MeterKeys.ENGINE_HOURS to 1041.8,
      "odometer" to 84512.0,
    ),
  )

  @Test
  fun aMeterWithAReadingResolvesWhateverItsKey() {
    // The aviation keys have no privileged path any more: an odometer is looked up identically.
    assertThat(stats.valueFor(MeterKeys.AIRFRAME_HOURS)).isEqualTo(1111.0)
    assertThat(stats.valueFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1041.8)
    assertThat(stats.valueFor("odometer")).isEqualTo(84512.0)
  }

  @Test
  fun aMeterNobodyHasRecordedIsNullRatherThanZero() {
    // The card draws an em dash for this. Zero would read as a real measurement — a bike claiming
    // it has been ridden nowhere rather than that nobody has logged a ride.
    assertThat(stats.valueFor(MeterKeys.PROP_HOURS)).isNull()
    assertThat(stats.valueFor("ride_hours")).isNull()
  }

  @Test
  fun aThingWithNoLogsAtAllHasNoReadings() {
    assertThat(LogStats(total = 0, airframe = 0, engine = 0, propeller = 0).valueFor(
      MeterKeys.ENGINE_HOURS,
    )).isNull()
  }
}
