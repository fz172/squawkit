package dev.fanfly.wingslog.feature.tasks.update.compose

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class BannerHelpersTest {

  // ── advanceDatePastToday ─────────────────────────────────────────────────

  @Test
  fun advanceDatePastToday_overdueMonths_advancesPastToday() {
    // natural was Apr 1, today is May 18, interval = 6 months → should advance to Oct 1.
    val natural = LocalDate(2026, 4, 1)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "6",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceDatePastToday(natural, schedule, today)

    assertThat(result).isEqualTo(LocalDate(2026, 10, 1))
  }

  @Test
  fun advanceDatePastToday_severelyOverdue_advancesPastAllOverdueCycles() {
    // natural was Jan 1, 2024 with monthly interval, today is May 18, 2026 → must keep
    // advancing past every overdue cycle, not just one.
    val natural = LocalDate(2024, 1, 1)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "12",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceDatePastToday(natural, schedule, today)

    assertThat(result).isEqualTo(LocalDate(2027, 1, 1))
  }

  @Test
  fun advanceDatePastToday_futureNaturalDue_advancesByExactlyOneInterval() {
    // natural is in the future (Jun 1) and today is May 18; mirrors TaskDueManagerImpl
    // which always advances at least once when force-complied is applied.
    val natural = LocalDate(2026, 6, 1)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "6",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceDatePastToday(natural, schedule, today)

    assertThat(result).isEqualTo(LocalDate(2026, 12, 1))
  }

  @Test
  fun advanceDatePastToday_daysUnit_advancesInDays() {
    val natural = LocalDate(2026, 4, 1)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "30",
      calUnit = ScheduleTimeUnit.DAYS,
    )

    val result = advanceDatePastToday(natural, schedule, today)

    // Apr 1 +30d = May 1; May 1 +30d = May 31 (past May 18) → stop.
    assertThat(result).isEqualTo(LocalDate(2026, 5, 31))
  }

  @Test
  fun advanceDatePastToday_yearsUnit_advancesInYears() {
    val natural = LocalDate(2020, 5, 18)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "2",
      calUnit = ScheduleTimeUnit.YEARS,
    )

    val result = advanceDatePastToday(natural, schedule, today)

    // 2020+2=2022; +2=2024; +2=2026 (==today, not strictly past); +2=2028
    assertThat(result).isEqualTo(LocalDate(2028, 5, 18))
  }

  @Test
  fun advanceDatePastToday_nullNatural_returnsNull() {
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "6",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceDatePastToday(null, schedule, LocalDate(2026, 5, 18))

    assertThat(result).isNull()
  }

  @Test
  fun advanceDatePastToday_hoursMode_returnsNull() {
    val natural = LocalDate(2026, 4, 1)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(mode = ScheduleMode.HOURS, hourValue = "100")

    val result = advanceDatePastToday(natural, schedule, today)

    assertThat(result).isNull()
  }

  @Test
  fun advanceDatePastToday_linkedMode_returnsNull() {
    val natural = LocalDate(2026, 4, 1)
    val today = LocalDate(2026, 5, 18)
    val schedule = ScheduleState(mode = ScheduleMode.LINKED, linkedToId = "parent-id")

    val result = advanceDatePastToday(natural, schedule, today)

    assertThat(result).isNull()
  }

  @Test
  fun advanceDatePastToday_invalidIntervalValue_returnsNull() {
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceDatePastToday(LocalDate(2026, 4, 1), schedule, LocalDate(2026, 5, 18))

    assertThat(result).isNull()
  }

  @Test
  fun advanceDatePastToday_zeroInterval_returnsNull() {
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "0",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceDatePastToday(LocalDate(2026, 4, 1), schedule, LocalDate(2026, 5, 18))

    assertThat(result).isNull()
  }

  // ── advanceEnginePastNow ─────────────────────────────────────────────────

  @Test
  fun advanceEnginePastNow_currentPastTarget_advancesPastCurrent() {
    // Target was 1450 hrs, current is 1500, interval = 100 → 1550.
    val result = advanceEnginePastNow(1450f, hoursSchedule("100"), 1500f)

    assertThat(result).isEqualTo(1550f)
  }

  @Test
  fun advanceEnginePastNow_currentFarPastTarget_advancesPastAllCycles() {
    val result = advanceEnginePastNow(1000f, hoursSchedule("100"), 1525f)

    assertThat(result).isEqualTo(1600f)
  }

  @Test
  fun advanceEnginePastNow_currentBeforeTarget_advancesByExactlyOneInterval() {
    val result = advanceEnginePastNow(1600f, hoursSchedule("100"), 1500f)

    assertThat(result).isEqualTo(1700f)
  }

  @Test
  fun advanceEnginePastNow_nullNatural_returnsNull() {
    val result = advanceEnginePastNow(null, hoursSchedule("100"), 1500f)

    assertThat(result).isNull()
  }

  @Test
  fun advanceEnginePastNow_timeMode_returnsNull() {
    val schedule = ScheduleState(
      mode = ScheduleMode.TIME,
      calValue = "6",
      calUnit = ScheduleTimeUnit.MONTHS,
    )

    val result = advanceEnginePastNow(1450f, schedule, 1500f)

    assertThat(result).isNull()
  }

  @Test
  fun advanceEnginePastNow_invalidInterval_returnsNull() {
    val result = advanceEnginePastNow(1450f, hoursSchedule(""), 1500f)

    assertThat(result).isNull()
  }

  @Test
  fun advanceEnginePastNow_zeroInterval_returnsNull() {
    val result = advanceEnginePastNow(1450f, hoursSchedule("0"), 1500f)

    assertThat(result).isNull()
  }

  // ── formatEngineHours ────────────────────────────────────────────────────

  @Test
  fun formatEngineHours_integerValue_hasNoDecimal() {
    assertThat(formatEngineHours(100f)).isEqualTo("100")
  }

  @Test
  fun formatEngineHours_decimalValue_keepsOneDecimal() {
    assertThat(formatEngineHours(100.5f)).isEqualTo("100.5")
  }

  @Test
  fun formatEngineHours_roundsToOneDecimal() {
    assertThat(formatEngineHours(100.567f)).isEqualTo("100.6")
  }

  @Test
  fun formatEngineHours_roundsDownToInteger_dropsDecimal() {
    // 100.04 rounds to 100.0 → integer formatting.
    assertThat(formatEngineHours(100.04f)).isEqualTo("100")
  }

  private fun hoursSchedule(hourValue: String) =
    ScheduleState(mode = ScheduleMode.HOURS, hourValue = hourValue)
}
