package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MeterReading
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
    // Saved back with no rule, the reading stays on the odometer.
    assertThat(state.toRules()).isEmpty()
    assertThat(state.forcedDueMeterKey(ComponentType.COMPONENT_UNKNOWN, emptyList()))
      .isEqualTo("odometer")
  }

  @Test
  fun aOneTimeItemDueByADateIsTrackedByCalendarOnce() {
    val state = ScheduleState.fromTask(
      MaintenanceTask(is_one_time = true, force_due_date = toWireInstant(1_800_000_000)),
    )

    assertThat(state.mode).isEqualTo(ScheduleMode.TIME)
    assertThat(state.recurrence).isEqualTo(ScheduleRecurrence.ONE_TIME)
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
