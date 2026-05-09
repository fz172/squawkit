package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBanner
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBannerTone
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.schedule_prefix_every
import wingslog.feature.tasks.update.generated.resources.schedule_prefix_in
import wingslog.feature.tasks.update.generated.resources.schedule_preview_asap_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_asap_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_every
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_every_hours
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_in
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_in_hours
import wingslog.feature.tasks.update.generated.resources.schedule_preview_empty_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_empty_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_hint
import wingslog.feature.tasks.update.generated.resources.schedule_preview_label
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_one_time_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_repeating_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_unset_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_unset_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_one_time_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_recurring_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_calendar_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_hours_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_asap
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_asap_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_linked_one_time_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_linked_repeating_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_one_time
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_one_time_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_repeating
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_repeating_sub
import wingslog.feature.tasks.update.generated.resources.schedule_step_interval_how_often
import wingslog.feature.tasks.update.generated.resources.schedule_step_interval_in_how_long
import wingslog.feature.tasks.update.generated.resources.schedule_step_recurrence_label
import wingslog.feature.tasks.update.generated.resources.schedule_step_recurrence_linked_label
import wingslog.feature.tasks.update.generated.resources.schedule_step_track_label
import wingslog.feature.tasks.update.generated.resources.schedule_unit_days
import wingslog.feature.tasks.update.generated.resources.schedule_unit_months
import wingslog.feature.tasks.update.generated.resources.schedule_unit_tach_hours
import wingslog.feature.tasks.update.generated.resources.schedule_unit_years

@Composable
fun TaskScheduleTab(
  state: ScheduleState,
  onChange: (ScheduleState) -> Unit,
  availableInspections: List<MaintenanceTask>,
  modifier: Modifier = Modifier,
) {
  var advancedOpen by remember(state.mode) { mutableStateOf(state.mode == ScheduleMode.LINKED) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    val (previewPrimary, previewSecondary, previewIsEmpty) = previewText(
      state = state,
      linkedTaskName = availableInspections.firstOrNull { it.id == state.linkedToId }?.title,
    )
    PreviewBanner(
      label = stringResource(Res.string.schedule_preview_label),
      hint = stringResource(Res.string.schedule_preview_hint),
      primary = previewPrimary,
      secondary = previewSecondary,
      tone = if (previewIsEmpty) PreviewBannerTone.Neutral else PreviewBannerTone.Active,
    )

    // Step 1 — How is this tracked?
    ScheduleSection(
      labelRes = Res.string.schedule_step_track_label,
      complete = state.mode != null,
    ) {
      TrackingModeChoice(
        selected = if (state.mode == ScheduleMode.LINKED) null else state.mode,
        onSelect = { picked ->
          // Switching mode resets dependent fields to avoid carrying stale values
          onChange(
            state.copy(
              mode = picked,
              linkedToId = null,
              recurrence = state.recurrence?.takeIf { it != ScheduleRecurrence.ASAP || picked != ScheduleMode.LINKED },
            )
          )
        },
      )
    }

    // Step 2 — Recurrence (with ASAP) for time/hours; without ASAP for linked
    if (state.mode == ScheduleMode.TIME || state.mode == ScheduleMode.HOURS) {
      ScheduleSection(
        labelRes = Res.string.schedule_step_recurrence_label,
        complete = state.recurrence != null,
      ) {
        RecurrenceChoice(
          selected = state.recurrence,
          options = listOf(
            ScheduleRecurrence.REPEATING to (Res.string.schedule_recurrence_repeating to Res.string.schedule_recurrence_repeating_sub),
            ScheduleRecurrence.ONE_TIME to (Res.string.schedule_recurrence_one_time to Res.string.schedule_recurrence_one_time_sub),
            ScheduleRecurrence.ASAP to (Res.string.schedule_recurrence_asap to Res.string.schedule_recurrence_asap_sub),
          ),
          onSelect = { onChange(state.copy(recurrence = it)) },
        )
      }
    } else if (state.mode == ScheduleMode.LINKED) {
      ScheduleSection(
        labelRes = Res.string.schedule_step_recurrence_linked_label,
        complete = state.recurrence != null,
      ) {
        RecurrenceChoice(
          selected = state.recurrence,
          options = listOf(
            ScheduleRecurrence.REPEATING to (Res.string.schedule_recurrence_repeating to Res.string.schedule_recurrence_linked_repeating_sub),
            ScheduleRecurrence.ONE_TIME to (Res.string.schedule_recurrence_one_time to Res.string.schedule_recurrence_linked_one_time_sub),
          ),
          onSelect = { onChange(state.copy(recurrence = it)) },
        )
      }
    }

    // Step 3 — Interval (hidden if ASAP or no recurrence picked)
    val showInterval = (state.mode == ScheduleMode.TIME || state.mode == ScheduleMode.HOURS) &&
      state.recurrence != null && state.recurrence != ScheduleRecurrence.ASAP
    if (showInterval) {
      val intervalLabel = if (state.recurrence == ScheduleRecurrence.ONE_TIME) {
        Res.string.schedule_step_interval_in_how_long
      } else {
        Res.string.schedule_step_interval_how_often
      }
      val complete = when (state.mode) {
        ScheduleMode.TIME -> state.calValue.isNotBlank()
        ScheduleMode.HOURS -> state.hourValue.isNotBlank()
      }
      ScheduleSection(
        labelRes = intervalLabel,
        complete = complete
      ) {
        when (state.mode) {
          ScheduleMode.TIME -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            UnitPillSelect(
              selected = state.calUnit,
              onSelect = { onChange(state.copy(calUnit = it)) },
            )
            IntervalNumberInput(
              value = state.calValue,
              onChange = { onChange(state.copy(calValue = it)) },
              suffix = stringResource(state.calUnit.label()),
              prefix = stringResource(
                if (state.recurrence == ScheduleRecurrence.ONE_TIME) Res.string.schedule_prefix_in
                else Res.string.schedule_prefix_every
              ),
              keyboard = KeyboardType.Number,
            )
          }

          ScheduleMode.HOURS -> IntervalNumberInput(
            value = state.hourValue,
            onChange = { onChange(state.copy(hourValue = it)) },
            suffix = stringResource(Res.string.schedule_unit_tach_hours),
            prefix = stringResource(
              if (state.recurrence == ScheduleRecurrence.ONE_TIME) Res.string.schedule_prefix_in
              else Res.string.schedule_prefix_every
            ),
            keyboard = KeyboardType.Decimal,
          )
        }
      }
    }

    AdvancedLinkedSection(
      open = advancedOpen,
      onToggle = { advancedOpen = !advancedOpen },
      isLinkedMode = state.mode == ScheduleMode.LINKED,
      linkedTask = availableInspections.firstOrNull { it.id == state.linkedToId },
      availableInspections = availableInspections,
      onPick = { picked ->
        onChange(
          state.copy(
            mode = ScheduleMode.LINKED,
            linkedToId = picked.id,
            recurrence = state.recurrence?.takeIf { it != ScheduleRecurrence.ASAP }
              ?: ScheduleRecurrence.REPEATING,
            // Clear interval values when switching to linked
            calValue = "",
            hourValue = "",
          )
        )
      },
      onClear = {
        onChange(
          state.copy(
            mode = null,
            linkedToId = null
          )
        )
      },
    )
  }
}

// ─── Section frame ──────────────────────────────────────────────────────────

@Composable
private fun ScheduleSection(
  labelRes: StringResource,
  complete: Boolean,
  content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      modifier = Modifier.padding(bottom = Spacing.small),
    ) {
      if (complete) {
        Box(
          modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          // Simple checkmark using a "✓" character keeps platform-agnostic
          Text(
            "✓",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
      Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.9.sp),
        fontWeight = FontWeight.Bold,
        color = if (complete) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    content()
  }
}

@Composable
private fun previewText(
  state: ScheduleState,
  linkedTaskName: String?,
): Triple<String, String, Boolean> {
  if (state.mode == null) {
    return Triple(
      stringResource(Res.string.schedule_preview_empty_primary),
      stringResource(Res.string.schedule_preview_empty_secondary),
      true,
    )
  }
  if (state.mode == ScheduleMode.LINKED) {
    if (state.linkedToId == null || linkedTaskName == null) {
      return Triple(
        stringResource(Res.string.schedule_preview_linked_unset_primary),
        stringResource(Res.string.schedule_preview_linked_unset_secondary),
        false,
      )
    }
    val secondary = if (state.recurrence == ScheduleRecurrence.ONE_TIME) {
      stringResource(Res.string.schedule_preview_linked_one_time_secondary)
    } else {
      stringResource(Res.string.schedule_preview_linked_repeating_secondary)
    }
    return Triple(
      stringResource(
        Res.string.schedule_preview_linked_primary,
        linkedTaskName
      ),
      secondary,
      false,
    )
  }
  if (state.recurrence == ScheduleRecurrence.ASAP) {
    return Triple(
      stringResource(Res.string.schedule_preview_asap_primary),
      stringResource(Res.string.schedule_preview_asap_secondary),
      false,
    )
  }
  if (state.mode == ScheduleMode.TIME) {
    val n = state.calValue.toIntOrNull()
    if (n == null) {
      return Triple(
        stringResource(
          Res.string.schedule_preview_set_calendar_primary,
          stringResource(state.calUnit.label())
        ),
        stringResource(Res.string.schedule_preview_set_secondary),
        false,
      )
    }
    val pluralUnit = stringResource(state.calUnit.label())
    val unitStr = if (n == 1) pluralUnit.removeSuffix("s") else pluralUnit
    val primaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
      Res.string.schedule_preview_due_in else Res.string.schedule_preview_due_every
    val secondaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
      Res.string.schedule_preview_one_time_secondary else Res.string.schedule_preview_recurring_secondary
    return Triple(
      stringResource(
        primaryRes,
        n,
        unitStr
      ),
      stringResource(secondaryRes),
      false
    )
  }
  // HOURS
  if (state.hourValue.isBlank()) {
    return Triple(
      stringResource(Res.string.schedule_preview_set_hours_primary),
      stringResource(Res.string.schedule_preview_set_secondary),
      false,
    )
  }
  val primaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
    Res.string.schedule_preview_due_in_hours else Res.string.schedule_preview_due_every_hours
  val secondaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
    Res.string.schedule_preview_one_time_secondary else Res.string.schedule_preview_recurring_secondary
  return Triple(
    stringResource(
      primaryRes,
      state.hourValue
    ),
    stringResource(secondaryRes),
    false
  )
}

private fun ScheduleTimeUnit.label(): StringResource = when (this) {
  ScheduleTimeUnit.DAYS -> Res.string.schedule_unit_days
  ScheduleTimeUnit.MONTHS -> Res.string.schedule_unit_months
  ScheduleTimeUnit.YEARS -> Res.string.schedule_unit_years
}

