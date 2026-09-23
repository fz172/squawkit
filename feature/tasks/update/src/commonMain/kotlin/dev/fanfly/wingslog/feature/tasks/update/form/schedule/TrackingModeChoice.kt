package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.scheduleTypesOffered
import dev.fanfly.wingslog.thing.MeterDef
import dev.fanfly.wingslog.thing.ScheduleType
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.schedule_track_calendar_time
import wingslog.feature.tasks.update.generated.resources.schedule_track_seasonal
import wingslog.feature.tasks.update.generated.resources.schedule_track_tach_hours

/**
 * The tracking modes the template's `schedule_types` lists (PRD §4.6) — calendar time, the
 * meter, the seasonal anchor — each as a button. A mode a stored task already uses stays visible
 * so the task can still be edited if its preset later stopped offering it.
 */
@Composable
internal fun TrackingModeChoice(
  selected: ScheduleMode?,
  onSelect: (ScheduleMode) -> Unit,
  /** The meter this task would count in — its label names the button ("Engine Time", "Odometer"). */
  meter: MeterDef?,
) {
  val capabilities = LocalThingCapabilities.current
  val offered = scheduleTypesOffered(capabilities.schedule_types)
  val options = buildList {
    if (ScheduleType.SCHEDULE_TYPE_CALENDAR in offered || selected == ScheduleMode.TIME) {
      add(
        SegmentOption(
          ScheduleMode.TIME,
          stringResource(Res.string.schedule_track_calendar_time),
          Icons.Default.CalendarToday,
        )
      )
    }
    if (ScheduleType.SCHEDULE_TYPE_METER in offered || selected == ScheduleMode.HOURS) {
      add(
        SegmentOption(
          ScheduleMode.HOURS,
          // The meter's own name — "Odometer" on a car, "Engine Time" for an engine task on an
          // aeroplane. A fixed "Tach Hours", and then a fixed first meter, both named the wrong one.
          meter?.label?.takeIf { it.isNotEmpty() }
            ?: stringResource(Res.string.schedule_track_tach_hours),
          Icons.Default.Schedule,
        )
      )
    }
    if (ScheduleType.SCHEDULE_TYPE_SEASONAL in offered || selected == ScheduleMode.SEASONAL) {
      add(
        SegmentOption(
          ScheduleMode.SEASONAL,
          stringResource(Res.string.schedule_track_seasonal),
          Icons.Default.EventRepeat,
        )
      )
    }
  }
  SegmentedChoice(options = options, selected = selected, onSelect = onSelect)
}
