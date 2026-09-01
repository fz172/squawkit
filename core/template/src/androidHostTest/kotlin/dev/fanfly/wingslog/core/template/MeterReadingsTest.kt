package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MeterReading
import org.junit.Test

/**
 * Reading meter values across the change from three hour fields to a declared set (#730).
 *
 * The fallback is the part that matters. `engine_hour`, `airframe_time` and `prop_time` hold the
 * value on every log written before this, and there are years of them — a reader consulting only
 * `readings` would report an aeroplane with a full logbook as having flown zero hours.
 */
class MeterReadingsTest {

  @Test
  fun aLogWrittenBeforeReadingsExistedStillAnswers() {
    val legacy = MaintenanceLog(
      id = "l1",
      engine_hour = 1041.8,
      airframe_time = 1111.0,
      prop_time = 1029.8,
    )

    assertThat(legacy.readingFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1041.8)
    assertThat(legacy.readingFor(MeterKeys.AIRFRAME_HOURS)).isEqualTo(1111.0)
    assertThat(legacy.readingFor(MeterKeys.PROP_HOURS)).isEqualTo(1029.8)
  }

  @Test
  fun readingsWinOverTheLegacyFieldTheyDuplicate() {
    // Both are written today, so they agree. If they ever did not, the keyed one is the newer.
    val log = MaintenanceLog(
      id = "l1",
      engine_hour = 100.0,
      readings = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 200.0)),
    )

    assertThat(log.readingFor(MeterKeys.ENGINE_HOURS)).isEqualTo(200.0)
  }

  @Test
  fun aMeterTheLogDidNotRecordIsNullRatherThanZero() {
    // A log that did not touch a meter is not a log reporting zero, and the difference decides
    // whether it counts toward the current reading.
    val log = MaintenanceLog(id = "l1", engine_hour = 100.0)

    assertThat(log.readingFor(MeterKeys.AIRFRAME_HOURS)).isNull()
    assertThat(log.readingFor("odometer")).isNull()
  }

  @Test
  fun aCarsOdometerIsCarriedLikeAnyOtherMeter() {
    // The whole point: the key carries the meaning, so a reading with no legacy field behind it
    // works exactly as the aviation ones do.
    val log = MaintenanceLog(
      id = "l1",
      readings = listOf(MeterReading("odometer", value_ = 84512.0)),
    )

    assertThat(log.readingFor("odometer")).isEqualTo(84512.0)
  }

  @Test
  fun theCurrentReadingIsTheMaximumAcrossLogs() {
    val logs = listOf(
      MaintenanceLog(id = "a", readings = listOf(MeterReading("odometer", value_ = 80000.0))),
      MaintenanceLog(id = "b", readings = listOf(MeterReading("odometer", value_ = 84512.0))),
      MaintenanceLog(id = "c", engine_hour = 1041.8),
    )

    val current = currentReadings(logs)

    assertThat(current.first { it.meter_key == "odometer" }.value_).isEqualTo(84512.0)
    // A legacy field contributes its key without any log carrying a `readings` entry for it.
    assertThat(current.first { it.meter_key == MeterKeys.ENGINE_HOURS }.value_)
      .isEqualTo(1041.8)
  }

  @Test
  fun aMeterNoLogTouchedIsAbsentRatherThanZero() {
    // So a reader can tell "not recorded yet" from "reads zero" — the difference between an
    // em dash and a number on the dashboard.
    val current = currentReadings(listOf(MaintenanceLog(id = "a", engine_hour = 10.0)))

    assertThat(current.map { it.meter_key }).containsExactly(MeterKeys.ENGINE_HOURS)
  }

  @Test
  fun anOverviewComputedBeforeCurrentExistedStillAnswers() {
    // Recomputed on the next log write, but until then the three doubles are all it has.
    val overview = MaintenanceOverview(
      aircraft_id = "t",
      current_engine_time = 1041.8,
    )

    assertThat(overview.currentFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1041.8)
    assertThat(overview.currentFor("odometer")).isNull()
  }

  @Test
  fun clearingAMeterRemovesItRatherThanStoringZero() {
    // Clearing the field means "I did not record this", which is what an absent reading says and
    // what a zero one does not.
    val readings = listOf(MeterReading("odometer", value_ = 100.0))

    assertThat(readings.withReading("odometer", null)).isEmpty()
    assertThat(readings.withReading("odometer", 200.0).single().value_).isEqualTo(200.0)
  }
}
