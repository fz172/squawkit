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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_preview_neutral_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_neutral_secondary_linked
import wingslog.feature.tasks.update.generated.resources.adj_preview_neutral_secondary_unset
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_date_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_hours_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_was_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_was_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_skip_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_skip_secondary
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_disabled_linked
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_disabled_unset
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_section_label
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_subtitle
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_title
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_was_date
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_was_hours
import wingslog.feature.tasks.update.generated.resources.adj_skip_section_label
import wingslog.feature.tasks.update.generated.resources.adj_skip_subtitle_active
import wingslog.feature.tasks.update.generated.resources.adj_skip_subtitle_inactive
import wingslog.feature.tasks.update.generated.resources.adj_skip_title_active
import wingslog.feature.tasks.update.generated.resources.adj_skip_title_inactive
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.select_date

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
  isSkipping: Boolean,
  onSkipToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val mode = schedule.mode
  val rescheduleOn = when (mode) {
    ScheduleMode.TIME -> forceOverrideDate
    ScheduleMode.HOURS -> forceOverrideEngine
    else -> false
  }

  fun setReschedule(on: Boolean) {
    when (mode) {
      ScheduleMode.TIME -> {
        onForceOverrideDateChange(on)
        if (on) onForceOverrideEngineChange(false)
      }
      ScheduleMode.HOURS -> {
        onForceOverrideEngineChange(on)
        if (on) onForceOverrideDateChange(false)
      }
      else -> {}
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    AdjustmentsPreviewBanner(
      rescheduleOn = rescheduleOn,
      isSkipping = isSkipping,
      mode = mode,
      forcedDateMillis = forcedDateMillis,
      forcedEngineHours = forcedEngineHours,
    )

    // Section 1 — Reschedule next due
    AdjSectionLabel(
      label = stringResource(Res.string.adj_reschedule_section_label),
      complete = rescheduleOn,
    )
    RescheduleCard(
      mode = mode,
      rescheduleOn = rescheduleOn,
      onToggle = { on ->
        setReschedule(on)
        if (on && isSkipping) onSkipToggle()
      },
      forcedEngineHours = forcedEngineHours,
      onForcedEngineHoursChange = onForcedEngineHoursChange,
      forcedDateMillis = forcedDateMillis,
      onDateClick = onDateClick,
    )

    // Section 2 — Skip this cycle
    AdjSectionLabel(
      label = stringResource(Res.string.adj_skip_section_label),
      complete = isSkipping,
    )
    SkipCard(
      isSkipping = isSkipping,
      onToggle = {
        if (!isSkipping && rescheduleOn) setReschedule(false)
        onSkipToggle()
      },
    )
  }
}

// ─── Preview banner ──────────────────────────────────────────────────────────

@Composable
private fun AdjustmentsPreviewBanner(
  rescheduleOn: Boolean,
  isSkipping: Boolean,
  mode: ScheduleMode?,
  forcedDateMillis: Long?,
  forcedEngineHours: String,
) {
  val warning = MaterialTheme.colorScheme.error
  val primary = MaterialTheme.colorScheme.primary
  val isNeutral = !rescheduleOn && !isSkipping

  val (bg, borderColor, iconTint, icon) = when {
    isSkipping -> BannerTokens(
      bg = warning.copy(alpha = 0.10f),
      border = warning.copy(alpha = 0.35f),
      tint = warning,
      icon = Icons.Default.FastForward,
    )
    rescheduleOn -> BannerTokens(
      bg = primary.copy(alpha = 0.10f),
      border = primary.copy(alpha = 0.35f),
      tint = primary,
      icon = Icons.Default.CalendarToday,
    )
    else -> BannerTokens(
      bg = MaterialTheme.colorScheme.surfaceContainer,
      border = MaterialTheme.colorScheme.outlineVariant,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      icon = Icons.Default.CalendarToday,
    )
  }

  val primaryText: String
  val secondaryText: String
  when {
    isSkipping -> {
      primaryText = stringResource(Res.string.adj_preview_skip_primary)
      secondaryText = stringResource(Res.string.adj_preview_skip_secondary)
    }
    rescheduleOn && mode == ScheduleMode.TIME -> {
      val dateStr = forcedDateMillis?.let {
        Instant.fromEpochMilliseconds(it)
          .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
      } ?: "—"
      primaryText = stringResource(Res.string.adj_preview_reschedule_date_primary, dateStr)
      secondaryText = stringResource(Res.string.adj_preview_reschedule_was_date)
    }
    rescheduleOn && mode == ScheduleMode.HOURS -> {
      primaryText = stringResource(
        Res.string.adj_preview_reschedule_hours_primary,
        forcedEngineHours.ifBlank { "—" }
      )
      secondaryText = stringResource(Res.string.adj_preview_reschedule_was_hours)
    }
    mode == ScheduleMode.LINKED -> {
      primaryText = stringResource(Res.string.adj_preview_neutral_primary)
      secondaryText = stringResource(Res.string.adj_preview_neutral_secondary_linked)
    }
    else -> {
      primaryText = stringResource(Res.string.adj_preview_neutral_primary)
      secondaryText = stringResource(Res.string.adj_preview_neutral_secondary_unset)
    }
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(bg)
      .border(1.dp, borderColor, RoundedCornerShape(14.dp))
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(iconTint.copy(alpha = if (isNeutral) 0f else 0.18f)
          .let { if (isNeutral) MaterialTheme.colorScheme.surfaceContainerHighest else it }),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = iconTint,
      )
    }
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        primaryText,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (isNeutral) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        secondaryText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private data class BannerTokens(
  val bg: androidx.compose.ui.graphics.Color,
  val border: androidx.compose.ui.graphics.Color,
  val tint: androidx.compose.ui.graphics.Color,
  val icon: ImageVector,
)

// ─── Reschedule section ──────────────────────────────────────────────────────

@Composable
private fun RescheduleCard(
  mode: ScheduleMode?,
  rescheduleOn: Boolean,
  onToggle: (Boolean) -> Unit,
  forcedEngineHours: String,
  onForcedEngineHoursChange: (String) -> Unit,
  forcedDateMillis: Long?,
  onDateClick: () -> Unit,
) {
  val isLinked = mode == ScheduleMode.LINKED
  val noMode = mode == null
  val disabled = isLinked || noMode
  val primary = MaterialTheme.colorScheme.primary
  val borderColor = if (rescheduleOn) primary else MaterialTheme.colorScheme.outlineVariant

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .alpha(if (disabled) 0.55f else 1f)
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(
          enabled = !disabled,
          role = Role.Switch,
        ) { onToggle(!rescheduleOn) }
        .padding(horizontal = Spacing.large, vertical = Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.adj_reschedule_title),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        val subtitle = when {
          isLinked -> stringResource(Res.string.adj_reschedule_disabled_linked)
          noMode -> stringResource(Res.string.adj_reschedule_disabled_unset)
          else -> stringResource(Res.string.adj_reschedule_subtitle)
        }
        Text(
          subtitle,
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
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(0.dp)
          )
          .padding(horizontal = Spacing.large, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        when (mode) {
          ScheduleMode.TIME -> {
            val dateStr = forcedDateMillis?.let {
              Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(role = Role.Button) { onDateClick() }
                .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
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
              suffix = "tach hrs",
              prefix = "At",
              keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal,
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

// ─── Skip section ────────────────────────────────────────────────────────────

@Composable
private fun SkipCard(isSkipping: Boolean, onToggle: () -> Unit) {
  val warning = MaterialTheme.colorScheme.error
  val borderColor = if (isSkipping) warning else MaterialTheme.colorScheme.outlineVariant
  val bg = if (isSkipping) warning.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(bg)
      .border(1.dp, borderColor, RoundedCornerShape(12.dp))
      .clickable(role = Role.Button) { onToggle() }
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(
          if (isSkipping) warning.copy(alpha = 0.18f)
          else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (isSkipping) Icons.Default.Check else Icons.Default.FastForward,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = if (isSkipping) warning else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        if (isSkipping) stringResource(Res.string.adj_skip_title_active)
        else stringResource(Res.string.adj_skip_title_inactive),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (isSkipping) warning else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        if (isSkipping) stringResource(Res.string.adj_skip_subtitle_active)
        else stringResource(Res.string.adj_skip_subtitle_inactive),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// ─── Section label ───────────────────────────────────────────────────────────

@Composable
private fun AdjSectionLabel(label: String, complete: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    if (complete) {
      Box(
        modifier = Modifier
          .size(14.dp)
          .clip(RoundedCornerShape(7.dp))
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
    Text(
      label,
      style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.9.sp),
      fontWeight = FontWeight.Bold,
      color = if (complete) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
