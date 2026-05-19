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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBanner
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBannerTone
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.select_date
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_preview_hint
import wingslog.feature.tasks.update.generated.resources.adj_preview_label_neutral
import wingslog.feature.tasks.update.generated.resources.adj_preview_label_warn
import wingslog.feature.tasks.update.generated.resources.adj_preview_neutral_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_neutral_secondary_linked
import wingslog.feature.tasks.update.generated.resources.adj_preview_neutral_secondary_unset
import wingslog.feature.tasks.update.generated.resources.adj_preview_primary_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_primary_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_day_ago
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_days_ago
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_due_today
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_hours_ago
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_hours_at
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_day
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_days
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_over_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_overdue_day
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_overdue_days
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_date_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_hours_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_reschedule_was_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_skip_primary
import wingslog.feature.tasks.update.generated.resources.adj_preview_skip_secondary
import wingslog.feature.tasks.update.generated.resources.adj_preview_skip_secondary_with_original_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_skip_secondary_with_original_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_was_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_was_hours
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
import wingslog.feature.tasks.update.generated.resources.schedule_preview_label
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant
import wingslog.core.ui.generated.resources.Res as CoreRes

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
  naturalDueDate: LocalDate?,
  naturalDueEngine: Float?,
  currentEngineHours: Float,
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

  val timeZone = TimeZone.currentSystemDefault()
  val today = remember { Clock.System.now().toLocalDateTime(timeZone).date }
  val rescheduledDate = forcedDateMillis?.let {
    Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).date
  }
  val rescheduledEngine = forcedEngineHours.toFloatOrNull()
  // When skip is applied, advance the natural rule-derived next-due past today
  // (or past current engine hours for hour-based rules) — mirrors the
  // force-complied advancement in TaskDueManagerImpl.
  val skippedDueDate = if (mode == ScheduleMode.TIME)
    advanceDatePastToday(naturalDueDate, schedule, today) else null
  val skippedDueEngine = if (mode == ScheduleMode.HOURS)
    advanceEnginePastNow(naturalDueEngine, schedule, currentEngineHours) else null

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    val bannerTone = when {
      isSkipping -> PreviewBannerTone.Warn
      rescheduleOn -> PreviewBannerTone.Active
      else -> PreviewBannerTone.Neutral
    }
    val bannerLabel = stringResource(
      when {
        isSkipping -> Res.string.adj_preview_label_warn
        rescheduleOn -> Res.string.schedule_preview_label
        else -> Res.string.adj_preview_label_neutral
      }
    )
    val bannerPrimary: AnnotatedString
    val bannerSecondary: AnnotatedString
    when {
      // ── Skip on, TIME mode with a computable advanced due ────────────────
      isSkipping && skippedDueDate != null -> {
        val skippedStr = skippedDueDate.toDisplayFormat()
        bannerPrimary = monoOn(
          stringResource(
            Res.string.adj_preview_primary_date,
            skippedStr,
            relativeDaysPhrase(today.daysUntil(skippedDueDate)),
          ),
          skippedStr,
        )
        bannerSecondary = if (naturalDueDate != null) {
          val natStr = naturalDueDate.toDisplayFormat()
          monoOn(
            stringResource(
              Res.string.adj_preview_skip_secondary_with_original_date,
              natStr,
              relativeDaysAgoPhrase(today.daysUntil(naturalDueDate)),
            ),
            natStr,
          )
        } else {
          AnnotatedString(stringResource(Res.string.adj_preview_skip_secondary))
        }
      }

      // ── Skip on, HOURS mode with a computable advanced due ───────────────
      isSkipping && skippedDueEngine != null -> {
        val skippedStr = formatEngineHours(skippedDueEngine)
        bannerPrimary = monoOn(
          stringResource(
            Res.string.adj_preview_primary_hours,
            skippedStr,
            relativeEnginePhrase(skippedDueEngine - currentEngineHours),
          ),
          skippedStr,
        )
        bannerSecondary = if (naturalDueEngine != null) {
          val natStr = formatEngineHours(naturalDueEngine)
          monoOn(
            stringResource(
              Res.string.adj_preview_skip_secondary_with_original_hours,
              natStr,
              relativeEngineAgoPhrase(naturalDueEngine - currentEngineHours),
            ),
            natStr,
          )
        } else {
          AnnotatedString(stringResource(Res.string.adj_preview_skip_secondary))
        }
      }

      // ── Skip on, no advanceable rule (LINKED / ASAP / no interval) ───────
      isSkipping -> {
        bannerPrimary = AnnotatedString(stringResource(Res.string.adj_preview_skip_primary))
        bannerSecondary = AnnotatedString(stringResource(Res.string.adj_preview_skip_secondary))
      }

      // ── Reschedule on, TIME mode ─────────────────────────────────────────
      rescheduleOn && mode == ScheduleMode.TIME -> {
        if (rescheduledDate != null) {
          val rescheduledStr = rescheduledDate.toDisplayFormat()
          bannerPrimary = monoOn(
            stringResource(
              Res.string.adj_preview_primary_date,
              rescheduledStr,
              relativeDaysPhrase(today.daysUntil(rescheduledDate)),
            ),
            rescheduledStr,
          )
          bannerSecondary = naturalDueDate?.let {
            val natStr = it.toDisplayFormat()
            monoOn(stringResource(Res.string.adj_preview_was_date, natStr), natStr)
          } ?: AnnotatedString(stringResource(Res.string.adj_preview_reschedule_was_date))
        } else {
          bannerPrimary = AnnotatedString(
            stringResource(Res.string.adj_preview_reschedule_date_primary, "—")
          )
          bannerSecondary =
            AnnotatedString(stringResource(Res.string.adj_preview_reschedule_was_date))
        }
      }

      // ── Reschedule on, HOURS mode ────────────────────────────────────────
      rescheduleOn && mode == ScheduleMode.HOURS -> {
        if (rescheduledEngine != null) {
          val rescheduledStr = formatEngineHours(rescheduledEngine)
          bannerPrimary = monoOn(
            stringResource(
              Res.string.adj_preview_primary_hours,
              rescheduledStr,
              relativeEnginePhrase(rescheduledEngine - currentEngineHours),
            ),
            rescheduledStr,
          )
          bannerSecondary = naturalDueEngine?.let {
            val natStr = formatEngineHours(it)
            monoOn(stringResource(Res.string.adj_preview_was_hours, natStr), natStr)
          } ?: AnnotatedString(stringResource(Res.string.adj_preview_reschedule_was_date))
        } else {
          bannerPrimary = AnnotatedString(
            stringResource(
              Res.string.adj_preview_reschedule_hours_primary,
              forcedEngineHours.ifBlank { "—" },
            )
          )
          bannerSecondary =
            AnnotatedString(stringResource(Res.string.adj_preview_reschedule_was_date))
        }
      }

      // ── Neutral, TIME mode with a known natural due ──────────────────────
      mode == ScheduleMode.TIME && naturalDueDate != null -> {
        val natStr = naturalDueDate.toDisplayFormat()
        bannerPrimary = monoOn(
          stringResource(
            Res.string.adj_preview_primary_date,
            natStr,
            relativeDaysPhrase(today.daysUntil(naturalDueDate)),
          ),
          natStr,
        )
        bannerSecondary = AnnotatedString("")
      }

      // ── Neutral, HOURS mode with a known natural due ─────────────────────
      mode == ScheduleMode.HOURS && naturalDueEngine != null -> {
        val natStr = formatEngineHours(naturalDueEngine)
        bannerPrimary = monoOn(
          stringResource(
            Res.string.adj_preview_primary_hours,
            natStr,
            relativeEnginePhrase(naturalDueEngine - currentEngineHours),
          ),
          natStr,
        )
        bannerSecondary = AnnotatedString("")
      }

      mode == ScheduleMode.LINKED -> {
        bannerPrimary = AnnotatedString(stringResource(Res.string.adj_preview_neutral_primary))
        bannerSecondary =
          AnnotatedString(stringResource(Res.string.adj_preview_neutral_secondary_linked))
      }

      else -> {
        bannerPrimary = AnnotatedString(stringResource(Res.string.adj_preview_neutral_primary))
        bannerSecondary =
          AnnotatedString(stringResource(Res.string.adj_preview_neutral_secondary_unset))
      }
    }
    PreviewBanner(
      label = bannerLabel,
      hint = stringResource(Res.string.adj_preview_hint),
      primary = bannerPrimary,
      secondary = bannerSecondary,
      tone = bannerTone,
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
          ScheduleMode.TIME -> {
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
private fun SkipCard(
  isSkipping: Boolean,
  onToggle: () -> Unit,
) {
  val warning = MaterialTheme.colorScheme.error
  val borderColor =
    if (isSkipping) warning else MaterialTheme.colorScheme.outlineVariant
  val bg =
    if (isSkipping) warning.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(bg)
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .clickable(role = Role.Button) { onToggle() }
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
        .clip(RoundedCornerShape(Spacing.smallCornerRadius))
        .background(
          if (isSkipping) warning.copy(alpha = 0.18f)
          else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (isSkipping) Icons.Default.Check else Icons.Default.FastForward,
        contentDescription = null,
        modifier = Modifier.size(Spacing.large),
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
    Text(
      label,
      style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.9.sp),
      fontWeight = FontWeight.Bold,
      color = if (complete) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

// ─── Banner helpers ──────────────────────────────────────────────────────────

internal fun advanceDatePastToday(
  natural: LocalDate?,
  schedule: ScheduleState,
  today: LocalDate,
): LocalDate? {
  if (natural == null || schedule.mode != ScheduleMode.TIME) return null
  val n = schedule.calValue.toIntOrNull() ?: return null
  if (n <= 0) return null
  // Always advance at least once (matches TaskDueManagerImpl force-complied path).
  var advanced = natural.step(schedule.calUnit, n)
  while (advanced <= today) advanced = advanced.step(schedule.calUnit, n)
  return advanced
}

private fun LocalDate.step(unit: ScheduleTimeUnit, n: Int): LocalDate = when (unit) {
  ScheduleTimeUnit.DAYS -> plus(n, DateTimeUnit.DAY)
  ScheduleTimeUnit.MONTHS -> plus(n, DateTimeUnit.MONTH)
  ScheduleTimeUnit.YEARS -> plus(n, DateTimeUnit.YEAR)
}

internal fun advanceEnginePastNow(
  natural: Float?,
  schedule: ScheduleState,
  currentEngineHours: Float,
): Float? {
  if (natural == null || schedule.mode != ScheduleMode.HOURS) return null
  val interval = schedule.hourValue.toFloatOrNull() ?: return null
  if (interval <= 0f) return null
  var advanced = natural + interval
  while (advanced <= currentEngineHours) advanced += interval
  return advanced
}

@Composable
private fun relativeDaysPhrase(days: Int): String = when {
  days == 0 -> stringResource(Res.string.adj_preview_rel_due_today)
  days == 1 -> stringResource(Res.string.adj_preview_rel_in_day)
  days > 1 -> stringResource(Res.string.adj_preview_rel_in_days, days)
  days == -1 -> stringResource(Res.string.adj_preview_rel_overdue_day)
  else -> stringResource(Res.string.adj_preview_rel_overdue_days, -days)
}

@Composable
private fun relativeDaysAgoPhrase(days: Int): String = when {
  days == 0 -> stringResource(Res.string.adj_preview_rel_due_today)
  days == -1 -> stringResource(Res.string.adj_preview_rel_day_ago)
  days < -1 -> stringResource(Res.string.adj_preview_rel_days_ago, -days)
  days == 1 -> stringResource(Res.string.adj_preview_rel_in_day)
  else -> stringResource(Res.string.adj_preview_rel_in_days, days)
}

@Composable
private fun relativeEnginePhrase(deltaHours: Float): String {
  val absDelta = abs(deltaHours)
  if (absDelta < 0.05f) return stringResource(Res.string.adj_preview_rel_hours_at)
  val formatted = formatEngineHours(absDelta)
  return if (deltaHours > 0f) {
    stringResource(Res.string.adj_preview_rel_in_hours, formatted)
  } else {
    stringResource(Res.string.adj_preview_rel_over_hours, formatted)
  }
}

@Composable
private fun relativeEngineAgoPhrase(deltaHours: Float): String {
  val absDelta = abs(deltaHours)
  if (absDelta < 0.05f) return stringResource(Res.string.adj_preview_rel_hours_at)
  val formatted = formatEngineHours(absDelta)
  return if (deltaHours < 0f) {
    stringResource(Res.string.adj_preview_rel_hours_ago, formatted)
  } else {
    stringResource(Res.string.adj_preview_rel_in_hours, formatted)
  }
}

internal fun formatEngineHours(value: Float): String {
  // Show integers without trailing decimal, otherwise one decimal place.
  val rounded = (value * 10f).roundToInt() / 10f
  return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString()
  else rounded.toString()
}

/**
 * Returns [text] as an AnnotatedString with each [fragment]'s first occurrence styled
 * in JetBrains Mono (via [WingslogTypography.dataMedium]). The surrounding text's
 * fontSize is preserved — only the font family and letter spacing are overridden.
 */
@Composable
private fun monoOn(text: String, vararg fragments: String): AnnotatedString {
  val monoFamily = WingslogTypography.dataMedium.fontFamily
  return buildAnnotatedString {
    append(text)
    val span = SpanStyle(fontFamily = monoFamily, letterSpacing = 0.sp)
    for (fragment in fragments) {
      if (fragment.isEmpty()) continue
      val idx = text.indexOf(fragment)
      if (idx >= 0) addStyle(span, idx, idx + fragment.length)
    }
  }
}
