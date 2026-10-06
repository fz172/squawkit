package dev.fanfly.wingslog.core.template

import com.google.common.truth.Truth.assertThat
import com.squareup.wire.Instant as WireInstant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.ManualMeterReading
import dev.fanfly.wingslog.thing.MeterReading
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/**
 * Reading meter values by key (#730).
 *
 * The three aviation doubles these replaced were retired in #761, after a backfill wrote the keyed
 * form onto every stored record. Nothing falls back any more, so a log reporting nothing for a
 * meter genuinely recorded nothing for it — which is why the null-versus-zero distinction below is
 * the load-bearing one.
 */
class MeterReadingsTest {

  @Test
  fun anAviationLogIsCarriedByKeyLikeAnyOther() {
    val log = MaintenanceLog(
      id = "l1",
      readings = listOf(
        MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1041.8),
        MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = 1111.0),
        MeterReading(MeterKeys.PROP_HOURS, value_ = 1029.8),
      ),
    )

    assertThat(log.readingFor(MeterKeys.ENGINE_HOURS)).isEqualTo(1041.8)
    assertThat(log.readingFor(MeterKeys.AIRFRAME_HOURS)).isEqualTo(1111.0)
    assertThat(log.readingFor(MeterKeys.PROP_HOURS)).isEqualTo(1029.8)
  }

  @Test
  fun aMeterTheLogDidNotRecordIsNullRatherThanZero() {
    // A log that did not touch a meter is not a log reporting zero, and the difference decides
    // whether it counts toward the current reading.
    val log = MaintenanceLog(
      id = "l1",
      readings = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 100.0)),
    )

    assertThat(log.readingFor(MeterKeys.AIRFRAME_HOURS)).isNull()
    assertThat(log.readingFor("odometer")).isNull()
  }

  @Test
  fun aReadingOfZeroCountsAsNotRecorded() {
    // The keyed form can hold an explicit zero where the doubles could not be told apart from
    // absence; both still mean "not recorded", so nothing downstream has to special-case it.
    val log = MaintenanceLog(
      id = "l1",
      readings = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 0.0)),
    )

    assertThat(log.readingFor(MeterKeys.ENGINE_HOURS)).isNull()
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
  fun theCurrentReadingIsTheMostRecentLogsNotTheHighest() {
    // It was the maximum, which no later reading could lower: a mistyped 84512 stuck until someone
    // found the log, and a replaced tach could not be recorded at all (#1368).
    val logs = listOf(
      odometerLog("a", day = MAY_1, value = 84512.0),
      odometerLog("b", day = MAY_3, value = 80000.0),
      MaintenanceLog(
        id = "c",
        timestamp = MAY_1.atMidnight(),
        readings = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1041.8))
      ),
    )

    val current = currentReadings(logs, timeZone = UTC)

    assertThat(current.first { it.meter_key == ODOMETER }.value_).isEqualTo(80000.0)
    // Meters are independent: a key only one log carries still reports that log’s value.
    assertThat(current.first { it.meter_key == MeterKeys.ENGINE_HOURS }.value_)
      .isEqualTo(1041.8)
  }

  @Test
  fun aBackdatedLogDoesNotMoveAManualReadingBack() {
    // Set on the dashboard on the 3rd; a log for work done on the 1st is entered the day after.
    val logs = listOf(
      odometerLog("late", day = MAY_1, value = 1150.0, savedAt = MAY_3.at(hour = 30))
    )
    val manual = listOf(manualOdometer(1200.0, setAt = MAY_3.at(hour = 9)))

    val current = currentReadingStates(logs, manual, UTC).single()

    assertThat(current.value).isEqualTo(1200.0)
    assertThat(current.isManual).isTrue()
    assertThat(current.asOf).isEqualTo(MAY_3)
  }

  @Test
  fun aLogDatedAfterAManualReadingSupersedesIt() {
    val logs = listOf(odometerLog("next", day = MAY_5, value = 1210.0))
    val manual = listOf(manualOdometer(1200.0, setAt = MAY_3.at(hour = 9)))

    val current = currentReadingStates(logs, manual, UTC).single()

    assertThat(current.value).isEqualTo(1210.0)
    assertThat(current.isManual).isFalse()
    assertThat(current.asOf).isEqualTo(MAY_5)
  }

  @Test
  fun onOneDayTheLastSavedWinsWhicheverItIs() {
    // A log’s timestamp is midnight of its work date, so against it a manual reading set that
    // morning would always look newer. The saved-at times are what order the day.
    val setAtNine = listOf(manualOdometer(1200.0, setAt = MAY_3.at(hour = 9)))

    val logSavedAfter = listOf(
      odometerLog("l", day = MAY_3, value = 1201.5, savedAt = MAY_3.at(hour = 17))
    )
    assertThat(currentReadings(logSavedAfter, setAtNine, UTC).single().value_)
      .isEqualTo(1201.5)

    val logSavedBefore = listOf(
      odometerLog("l", day = MAY_3, value = 1201.5, savedAt = MAY_3.at(hour = 8))
    )
    assertThat(currentReadings(logSavedBefore, setAtNine, UTC).single().value_)
      .isEqualTo(1200.0)
  }

  @Test
  fun twoLogsOnOneDayAreOrderedBySavedAtTooEvenDownward() {
    val logs = listOf(
      odometerLog("typo", day = MAY_3, value = 12015.0, savedAt = MAY_3.at(hour = 8)),
      odometerLog("fixed", day = MAY_3, value = 1201.5, savedAt = MAY_3.at(hour = 9)),
    )

    assertThat(currentReadings(logs, timeZone = UTC).single().value_).isEqualTo(1201.5)
  }

  @Test
  fun aLogSavedBeforeSavedAtExistedLosesItsDayToAnythingThatHasOne() {
    // No saved-at means it was written by a build that predates the field — so before whatever
    // does carry one.
    val logs = listOf(odometerLog("old", day = MAY_3, value = 1300.0))
    val manual = listOf(manualOdometer(1200.0, setAt = MAY_3.at(hour = 9)))

    assertThat(currentReadings(logs, manual, UTC).single().value_).isEqualTo(1200.0)
  }

  @Test
  fun twoSuchOlderLogsOnOneDayFallBackToTheHigherValue() {
    // Nothing orders them, and the maximum is what they were always read by.
    val logs = listOf(
      odometerLog("a", day = MAY_3, value = 1300.0),
      odometerLog("b", day = MAY_3, value = 1250.0),
    )

    assertThat(currentReadings(logs, timeZone = UTC).single().value_).isEqualTo(1300.0)
  }

  @Test
  fun theDayIsTheOneInTheReadersTimeZone() {
    // 03:00 UTC on the 4th is still the 3rd in New York, where a log for the 4th is a day later.
    val newYork = TimeZone.of("America/New_York")
    val logs = listOf(
      MaintenanceLog(
        id = "l",
        timestamp = MAY_4.atStartOfDayIn(newYork).toWireInstant(),
        readings = listOf(MeterReading(ODOMETER, value_ = 1150.0)),
      )
    )
    val manual = listOf(manualOdometer(1200.0, setAt = MAY_4.at(hour = 3)))

    assertThat(currentReadings(logs, manual, newYork).single().value_).isEqualTo(1150.0)
  }

  @Test
  fun aManualReadingAloneIsTheCurrentReading() {
    // Setting a reading is also how a Thing with no logs gets its first one.
    val manual = listOf(manualOdometer(42000.0, setAt = MAY_3.at(hour = 9)))

    val current = currentReadingStates(emptyList(), manual, UTC).single()

    assertThat(current.meterKey).isEqualTo(ODOMETER)
    assertThat(current.value).isEqualTo(42000.0)
  }

  @Test
  fun aManualReadingOfZeroCountsAsNotRecorded() {
    val manual = listOf(manualOdometer(0.0, setAt = MAY_3.at(hour = 9)))

    assertThat(currentReadings(emptyList(), manual, UTC)).isEmpty()
  }

  @Test
  fun aMeterNoLogTouchedIsAbsentRatherThanZero() {
    // So a reader can tell "not recorded yet" from "reads zero" — the difference between an
    // em dash and a number on the dashboard.
    val current = currentReadings(
      listOf(
        MaintenanceLog(
          id = "a",
          readings = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 10.0))
        )
      )
    )

    assertThat(current.map { it.meter_key }).containsExactly(MeterKeys.ENGINE_HOURS)
  }

  @Test
  fun theOverviewAnswersByKeyAndIsSilentAboutMetersItHasNot() {
    val overview = MaintenanceOverview(
      aircraft_id = "t",
      current = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1041.8)),
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
    assertThat(
      readings.withReading("odometer", 200.0)
        .single().value_
    ).isEqualTo(200.0)
  }

  // --- A due value renders in its meter's unit, not always in hours ---

  @Test
  fun aValueRendersInItsMetersUnit() {
    // The bug: a car scheduled every 5,000 miles read "5000.0 HRS" on its card while the editor
    // that created it said "mi".
    val automotive = CanonicalTemplates.AUTOMOTIVE

    assertThat(
      automotive.formatMeterValue(
        "odometer",
        5000.0
      )
    ).isEqualTo("5000 MI")
  }

  @Test
  fun anOdometerDropsTheDecimalPointItsMeterDoesNotTake() {
    assertThat(
      CanonicalTemplates.AUTOMOTIVE.formatMeterValue(
        "odometer",
        84512.0
      )
    )
      .isEqualTo("84512 MI")
    // Hours keep theirs.
    assertThat(
      AirplaneTemplate.TEMPLATE.formatMeterValue(
        MeterKeys.ENGINE_HOURS,
        100.0
      )
    )
      .isEqualTo("100.0 HRS")
  }

  @Test
  fun aValueWithNoMeterKeyStillReadsAsHours() {
    // Every value written before meter rules existed meant engine hours, so an unkeyed one has to
    // keep saying so rather than losing its unit.
    assertThat(AirplaneTemplate.TEMPLATE.formatMeterValue(null, 1041.8))
      .isEqualTo("1041.8 HRS")
    assertThat(AirplaneTemplate.TEMPLATE.formatMeterValue("nonesuch", 10.0))
      .isEqualTo("10.0 HRS")
  }

  // --- The one reading a summary row leads with ---

  @Test
  fun theLeadReadingIsTheFirstDeclaredMeterTheLogRecorded() {
    // The log detail sheet and the dashboard's activity row each show one headline number. They
    // picked it by switching on `component_type` across the three aviation fields, so a car's log
    // matched no branch and rendered a blank (#761).
    val log = MaintenanceLog(
      id = "l1",
      readings = listOf(MeterReading("odometer", value_ = 84512.0)),
    )

    val (meter, value) = CanonicalTemplates.AUTOMOTIVE.primaryReading(log)!!

    assertThat(meter.key).isEqualTo("odometer")
    assertThat(value).isEqualTo(84512.0)
  }

  @Test
  fun theLeadReadingFollowsDeclarationOrder() {
    // The airplane lists airframe hours first, so a log carrying several leads with that one.
    val log = MaintenanceLog(
      id = "l1",
      readings = listOf(
        MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1041.8),
        MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = 1111.0),
      ),
    )

    assertThat(AirplaneTemplate.TEMPLATE.primaryReading(log)?.meter?.key)
      .isEqualTo(MeterKeys.AIRFRAME_HOURS)
  }

  @Test
  fun anEngineLogLeadsWithEngineHours() {
    // Work on the engine is read against the engine's tach, even when the log noted airframe time.
    val log = MaintenanceLog(
      id = "l1",
      component_type = ComponentType.COMPONENT_ENGINE,
      readings = listOf(
        MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1041.8),
        MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = 1111.0),
      ),
    )

    assertThat(AirplaneTemplate.TEMPLATE.primaryReading(log)?.meter?.key)
      .isEqualTo(MeterKeys.ENGINE_HOURS)
  }

  @Test
  fun anEngineLogWithoutEngineHoursFallsBackToWhatItRecorded() {
    val log = MaintenanceLog(
      id = "l1",
      component_type = ComponentType.COMPONENT_ENGINE,
      readings = listOf(
        MeterReading(
          MeterKeys.AIRFRAME_HOURS,
          value_ = 1111.0
        )
      ),
    )

    assertThat(AirplaneTemplate.TEMPLATE.primaryReading(log)?.meter?.key)
      .isEqualTo(MeterKeys.AIRFRAME_HOURS)
  }

  @Test
  fun theTimelineStaysOnTheFirstMeterWhateverTheComponent() {
    val log = MaintenanceLog(
      id = "l1",
      component_type = ComponentType.COMPONENT_ENGINE,
      readings = listOf(
        MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1041.8),
        MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = 1111.0),
      ),
    )

    assertThat(AirplaneTemplate.TEMPLATE.timelineReading(log)?.value).isEqualTo(
      1111.0
    )
  }

  @Test
  fun aLogThatRecordedNoMeterHasNoLeadReading() {
    // Null, so the caller renders nothing rather than a zero it would have to explain.
    assertThat(AirplaneTemplate.TEMPLATE.primaryReading(MaintenanceLog(id = "l1"))).isNull()
    assertThat(CanonicalTemplates.HOME.primaryReading(MaintenanceLog(id = "l1"))).isNull()
  }

  @Test
  fun theNumberAndItsUnitSplitTheWayALayoutNeedsThem() {
    // Some layouts render the value large and the unit beside it, baseline-aligned.
    assertThat(
      CanonicalTemplates.AUTOMOTIVE.formatMeterNumber(
        "odometer",
        84512.0
      )
    )
      .isEqualTo("84512")
    assertThat(CanonicalTemplates.AUTOMOTIVE.meterUnit("odometer")).isEqualTo("MI")
    assertThat(AirplaneTemplate.TEMPLATE.meterUnit(MeterKeys.ENGINE_HOURS)).isEqualTo(
      "HRS"
    )
    // An unkeyed value still reads as hours, which is what it always meant.
    assertThat(AirplaneTemplate.TEMPLATE.meterUnit(null)).isEqualTo("HRS")
  }

  private fun odometerLog(
    id: String,
    day: LocalDate,
    value: Double,
    savedAt: WireInstant? = null,
  ) = MaintenanceLog(
    id = id,
    timestamp = day.atMidnight(),
    readings = listOf(MeterReading(ODOMETER, value_ = value)),
    readings_saved_at = savedAt,
  )

  private fun manualOdometer(value: Double, setAt: WireInstant) =
    ManualMeterReading(
      reading = MeterReading(ODOMETER, value_ = value),
      set_at = setAt,
    )

  // Midnight of the work date, the way the log form stamps it.
  private fun LocalDate.atMidnight(): WireInstant = at(hour = 0)

  /** [hour] past midnight UTC — past 24 runs into the next day. */
  private fun LocalDate.at(hour: Int): WireInstant =
    (atStartOfDayIn(UTC) + hour.hours).toWireInstant()

  private companion object {
    const val ODOMETER = "odometer"
    val UTC = TimeZone.UTC
    val MAY_1 = LocalDate(2026, 5, 1)
    val MAY_3 = LocalDate(2026, 5, 3)
    val MAY_4 = LocalDate(2026, 5, 4)
    val MAY_5 = LocalDate(2026, 5, 5)
  }
}
