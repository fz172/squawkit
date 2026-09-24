package dev.fanfly.wingslog.feature.tasks.update.form.adjustments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meterForComponent
import dev.fanfly.wingslog.core.ui.form.DangerZone
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.update.form.schedule.DueSummaryBanner
import dev.fanfly.wingslog.feature.tasks.update.form.schedule.ScheduleMode
import dev.fanfly.wingslog.feature.tasks.update.form.schedule.ScheduleState
import dev.fanfly.wingslog.thing.ComponentType
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_section_label
import wingslog.feature.tasks.update.generated.resources.delete_this_task_subtitle
import wingslog.feature.tasks.update.generated.resources.delete_this_task_title
import wingslog.feature.tasks.update.generated.resources.schedule_unit_tach_hours

@Composable
fun TaskAdjustmentsTab(
  schedule: ScheduleState,
  forceOverrideEngine: Boolean,
  onForceOverrideEngineChange: (Boolean) -> Unit,
  forcedEngineHours: String,
  onForcedEngineHoursChange: (String) -> Unit,
  forceOverrideDate: Boolean,
  onForceOverrideDateChange: (Boolean) -> Unit,
  forcedDateMillis: Long?,
  onDateClick: () -> Unit,
  /** The draft's due with and without its override — the same inputs the schedule tab shows. */
  effectiveDue: DueMetadata?,
  naturalDue: DueMetadata?,
  currentReading: (String) -> Float,
  linkedTaskName: String?,
  component: ComponentType,
  onDeleteRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val mode = schedule.mode
  // A seasonal schedule is a date schedule for every purpose here: it reschedules by date.
  val datedMode = mode == ScheduleMode.TIME || mode == ScheduleMode.SEASONAL
  val rescheduleOn = when {
    datedMode -> forceOverrideDate
    mode == ScheduleMode.HOURS -> forceOverrideEngine
    else -> false
  }

  fun setReschedule(on: Boolean) {
    when {
      datedMode -> {
        onForceOverrideDateChange(on)
        if (on) onForceOverrideEngineChange(false)
      }

      mode == ScheduleMode.HOURS -> {
        onForceOverrideEngineChange(on)
        if (on) onForceOverrideDateChange(false)
      }
    }
  }

  // The meter the schedule counts in, so every banner here says "mi" where the input says "mi"
  // (#785). Same resolution as the schedule tab — by component on the airplane — so the two tabs
  // name the same meter; the aviation word only when the template declares no meter at all.
  val meter = LocalThingTemplate.current.meterForComponent(component)
  val meterUnit = meter?.unit_label?.takeIf { it.isNotEmpty() }
    ?: stringResource(Res.string.schedule_unit_tach_hours)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    DueSummaryBanner(
      schedule = schedule,
      linkedTaskName = linkedTaskName,
      meterUnit = meterUnit,
      overrideOn = rescheduleOn,
      effectiveDue = effectiveDue,
      naturalDue = naturalDue,
      currentReading = currentReading,
    )

    // Section 1 — Reschedule next due
    AdjSectionLabel(
      label = stringResource(Res.string.adj_reschedule_section_label),
      complete = rescheduleOn,
    )
    RescheduleCard(
      mode = mode,
      rescheduleOn = rescheduleOn,
      onToggle = { on -> setReschedule(on) },
      forcedEngineHours = forcedEngineHours,
      onForcedEngineHoursChange = onForcedEngineHoursChange,
      forcedDateMillis = forcedDateMillis,
      onDateClick = onDateClick,
      meter = meter,
      meterUnit = meterUnit,
    )

    DangerZone(
      title = stringResource(Res.string.delete_this_task_title),
      subtitle = stringResource(Res.string.delete_this_task_subtitle),
      onDelete = onDeleteRequest,
    )
  }
}
