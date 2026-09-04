package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.thing.MeterDef
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.core.sharedassets.generated.resources.select_date
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_disabled_linked
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_disabled_unset
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_prefix_at
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_section_label
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_subtitle
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_title
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_was_date
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_was_hours
import wingslog.feature.tasks.update.generated.resources.delete_task_section_label
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
  // (#785). Same resolution as the schedule tab; the aviation word only when the template
  // declares no meter at all.
  val template = LocalThingTemplate.current
  val meter = template.meter(schedule.meterKey) ?: template?.meters?.firstOrNull()
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

    // Delete task — kept separate from the Resolve menu (Create Work Log / Skip This Cycle)
    // since deletion has no squawk-resolve analog.
    AdjSectionLabel(
      label = stringResource(Res.string.delete_task_section_label),
      complete = false,
    )
    DeleteTaskCard(onClick = onDeleteRequest)
  }
}

// ─── Reschedule section ──────────────────────────────────────────────────────

/**
 * The forced next-due controls: a switch, then a date or a meter reading. Shared with the create
 * form's "First due" section, which is the same override written once — the first cycle is
 * whatever the user says, and the schedule takes over after the first log clears it.
 */
@Composable
internal fun RescheduleCard(
  mode: ScheduleMode?,
  rescheduleOn: Boolean,
  onToggle: (Boolean) -> Unit,
  forcedEngineHours: String,
  onForcedEngineHoursChange: (String) -> Unit,
  forcedDateMillis: Long?,
  onDateClick: () -> Unit,
  meter: MeterDef?,
  meterUnit: String,
  title: String = stringResource(Res.string.adj_reschedule_title),
  subtitle: String = stringResource(Res.string.adj_reschedule_subtitle),
) {
  val isLinked = mode == ScheduleMode.LINKED
  val noMode = mode == null
  val disabled = isLinked || noMode
  val primary = MaterialTheme.colorScheme.primary
  val borderColor =
    if (rescheduleOn) primary else MaterialTheme.colorScheme.outlineVariant

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .alpha(if (disabled) 0.55f else 1f)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(
          enabled = !disabled,
          role = Role.Switch,
        ) { onToggle(!rescheduleOn) }
        .padding(
          horizontal = Spacing.large,
          vertical = Spacing.medium
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        val caption = when {
          isLinked -> stringResource(Res.string.adj_reschedule_disabled_linked)
          noMode -> stringResource(Res.string.adj_reschedule_disabled_unset)
          else -> subtitle
        }
        Text(
          caption,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = rescheduleOn && !disabled,
        onCheckedChange = null,
        enabled = !disabled,
      )
    }

    AnimatedVisibility(
      visible = rescheduleOn && !disabled,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .border(
            width = Spacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(Spacing.none)
          )
          .padding(
            horizontal = Spacing.large,
            vertical = Spacing.medium
          ),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        when (mode) {
          ScheduleMode.TIME, ScheduleMode.SEASONAL -> {
            val dateStr = forcedDateMillis?.let {
              Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Spacing.cardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(role = Role.Button) { onDateClick() }
                .padding(
                  horizontal = Spacing.medium,
                  vertical = Spacing.medium
                ),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(Spacing.large),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                dateStr ?: stringResource(CoreRes.string.select_date),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (dateStr != null) FontWeight.Bold else FontWeight.Normal,
                color = if (dateStr != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (forcedDateMillis != null) {
              Text(
                stringResource(Res.string.adj_reschedule_was_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          ScheduleMode.HOURS -> {
            IntervalNumberInput(
              value = forcedEngineHours,
              onChange = { onForcedEngineHoursChange(it.filter { c -> c.isDigit() || c == '.' }) },
              suffix = meterUnit,
              prefix = stringResource(Res.string.adj_reschedule_prefix_at),
              keyboard = if (meter?.decimal != false) KeyboardType.Decimal else KeyboardType.Number,
            )
            if (forcedEngineHours.isNotBlank()) {
              Text(
                stringResource(Res.string.adj_reschedule_was_hours),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          else -> Unit
        }
      }
    }
  }
}

// ─── Delete task section ─────────────────────────────────────────────────────

@Composable
private fun DeleteTaskCard(onClick: () -> Unit) {
  val critical = MaterialTheme.statusColors.critical.accent

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        critical.copy(alpha = 0.4f),
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .clickable(role = Role.Button, onClick = onClick)
      .padding(
        horizontal = Spacing.large,
        vertical = Spacing.medium
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Box(
      modifier = Modifier
        .size(Spacing.huge)
        .clip(RoundedCornerShape(percent = 50))
        .background(critical.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Default.Delete,
        contentDescription = null,
        modifier = Modifier.size(Spacing.large),
        tint = critical,
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        stringResource(Res.string.delete_this_task_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = critical,
      )
      Text(
        stringResource(Res.string.delete_this_task_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ─── Section label ───────────────────────────────────────────────────────────

@Composable
private fun AdjSectionLabel(
  label: String,
  complete: Boolean,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    if (complete) {
      Box(
        modifier = Modifier
          .size(Spacing.medium)
          .clip(RoundedCornerShape(Spacing.extraSmall))
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "✓",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
    FormSectionLabel(text = label)
  }
}
