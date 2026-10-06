package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.feature.tasks.datamanager.toDueInstant
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MeterReading
import kotlinx.datetime.LocalDate
import org.junit.Test

class ScheduleStateTest {

  @Test
  fun aOneTimeItemDueByAReadingIsTrackedOnThatMeterOnce() {
    // "First 25-hour inspection": no rule, only the reading it is due at.
    val state = ScheduleState.fromTask(
      MaintenanceTask(
        is_one_time = true,
        force_due_meter = MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = 25.0),
      ),
    )

    assertThat(state.mode).isEqualTo(ScheduleMode.HOURS)
    assertThat(state.recurrence).isEqualTo(ScheduleRecurrence.ONE_TIME)
    assertThat(state.meterKey).isEqualTo(MeterKeys.AIRFRAME_HOURS)
    assertThat(state.hourValue).isEqualTo("25")
    assertThat(state.isOneTime).isTrue()
  }

  @Test
  fun theMeterIsTheReadingsOwnOnAnyThing() {
    // "First service at 600 mi" on a motorcycle.
    val state = ScheduleState.fromTask(
      MaintenanceTask(
        is_one_time = true,
        force_due_meter = MeterReading("odometer", value_ = 600.0),
      ),
    )

    assertThat(state.mode).isEqualTo(ScheduleMode.HOURS)
    assertThat(state.meterKey).isEqualTo("odometer")
    // Once, 600 mi: the point it is due at is its "in how long".
    assertThat(state.hourValue).isEqualTo("600")
    val rules = state.toRules()
    assertThat(rules.single().meter_rule)
      .isEqualTo(MeterRule(meter_key = "odometer", interval = 600f))
    // The reading it is due at stays on the odometer.
    assertThat(state.forcedDueMeterKey(ComponentType.COMPONENT_UNKNOWN, rules))
      .isEqualTo("odometer")
  }

  @Test
  fun aOneTimeItemDueByADateIsTrackedByCalendarOnce() {
    val due = LocalDate(2027, 3, 31)
    val task = MaintenanceTask(is_one_time = true, force_due_date = due.toDueInstant())

    val state = ScheduleState.fromTask(task, today = LocalDate(2027, 3, 1))

    assertThat(state.mode).isEqualTo(ScheduleMode.TIME)
    assertThat(state.recurrence).isEqualTo(ScheduleRecurrence.ONE_TIME)
    // In 30 days.
    assertThat(state.calValue).isEqualTo("30")
    assertThat(state.calUnit).isEqualTo(ScheduleTimeUnit.DAYS)
    // One already past has no "in how long".
    assertThat(ScheduleState.fromTask(task, today = LocalDate(2027, 4, 2)).calValue).isEmpty()
  }

  @Test
  fun aRulesMeterStillDecidesWhereAnOverrideIsRead() {
    val rules = listOf(
      InspectionRule(meter_rule = MeterRule(meter_key = "odometer", interval = 5000f)),
    )
    val state = ScheduleState(mode = ScheduleMode.HOURS, meterKey = MeterKeys.ENGINE_HOURS)

    assertThat(state.forcedDueMeterKey(ComponentType.COMPONENT_ENGINE, rules))
      .isEqualTo("odometer")
  }

  @Test
  fun aTaskWithNoRuleAndNoDueHasNothingSelected() {
    assertThat(ScheduleState.fromTask(MaintenanceTask(title = "Wash"))).isEqualTo(ScheduleState())
  }
}
