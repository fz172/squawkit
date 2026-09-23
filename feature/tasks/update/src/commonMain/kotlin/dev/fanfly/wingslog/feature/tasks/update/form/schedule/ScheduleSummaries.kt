package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_due_today
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_hours_at
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_day
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_days
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_over_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_overdue_day
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_overdue_days
import wingslog.feature.tasks.update.generated.resources.schedule_preview_asap_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_asap_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_every
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_every_hours
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_in
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_in_hours
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_seasonal
import wingslog.feature.tasks.update.generated.resources.schedule_preview_due_seasonal_once
import wingslog.feature.tasks.update.generated.resources.schedule_preview_empty_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_empty_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_one_time_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_repeating_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_unset_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_linked_unset_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_one_time_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_recurring_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_calendar_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_hours_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_months_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_unit_days
import wingslog.feature.tasks.update.generated.resources.schedule_unit_months
import wingslog.feature.tasks.update.generated.resources.schedule_unit_years

/** The schedule in one sentence, a hint for the second line, and whether there is one at all. */
@Composable
internal fun scheduleSummary(
  state: ScheduleState,
  linkedTaskName: String?,
  meterUnit: String,
): ScheduleSummary {
  if (state.mode == null) {
    return ScheduleSummary(
      stringResource(Res.string.schedule_preview_empty_primary),
      stringResource(Res.string.schedule_preview_empty_secondary),
      true,
    )
  }
  if (state.mode == ScheduleMode.LINKED) {
    if (state.linkedToId == null || linkedTaskName == null) {
      return ScheduleSummary(
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
    return ScheduleSummary(
      stringResource(
        Res.string.schedule_preview_linked_primary,
        linkedTaskName
      ),
      secondary,
      false,
    )
  }
  if (state.mode == ScheduleMode.SEASONAL) {
    if (state.seasonalMonths.isEmpty()) {
      return ScheduleSummary(
        stringResource(Res.string.schedule_preview_set_months_primary),
        stringResource(Res.string.schedule_preview_set_secondary),
        false,
      )
    }
    val months = formatMonthList(state.seasonalMonths)
    return if (state.recurrence == ScheduleRecurrence.ONE_TIME) {
      ScheduleSummary(
        stringResource(Res.string.schedule_preview_due_seasonal_once, months),
        stringResource(Res.string.schedule_preview_one_time_secondary),
        false,
      )
    } else {
      ScheduleSummary(
        stringResource(Res.string.schedule_preview_due_seasonal, months),
        stringResource(Res.string.schedule_preview_recurring_secondary),
        false,
      )
    }
  }
  if (state.recurrence == ScheduleRecurrence.ASAP) {
    return ScheduleSummary(
      stringResource(Res.string.schedule_preview_asap_primary),
      stringResource(Res.string.schedule_preview_asap_secondary),
      false,
    )
  }
  if (state.mode == ScheduleMode.TIME) {
    val n = state.calValue.toIntOrNull()
    if (n == null) {
      return ScheduleSummary(
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
    return ScheduleSummary(
      stringResource(primaryRes, n, unitStr),
      stringResource(secondaryRes),
      false
    )
  }
  // HOURS
  if (state.hourValue.isBlank()) {
    return ScheduleSummary(
      stringResource(Res.string.schedule_preview_set_hours_primary, meterUnit),
      stringResource(Res.string.schedule_preview_set_secondary),
      false,
    )
  }
  val primaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
    Res.string.schedule_preview_due_in_hours else Res.string.schedule_preview_due_every_hours
  val secondaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
    Res.string.schedule_preview_one_time_secondary else Res.string.schedule_preview_recurring_secondary
  return ScheduleSummary(
    stringResource(primaryRes, state.hourValue, meterUnit),
    stringResource(secondaryRes),
    false
  )
}

internal fun ScheduleTimeUnit.label(): StringResource = when (this) {
  ScheduleTimeUnit.DAYS -> Res.string.schedule_unit_days
  ScheduleTimeUnit.MONTHS -> Res.string.schedule_unit_months
  ScheduleTimeUnit.YEARS -> Res.string.schedule_unit_years
}

@Composable
internal fun relativeDaysPhrase(days: Int): String = when {
  days == 0 -> stringResource(Res.string.adj_preview_rel_due_today)
  days == 1 -> stringResource(Res.string.adj_preview_rel_in_day)
  days > 1 -> stringResource(Res.string.adj_preview_rel_in_days, days)
  days == -1 -> stringResource(Res.string.adj_preview_rel_overdue_day)
  else -> stringResource(Res.string.adj_preview_rel_overdue_days, -days)
}

@Composable
internal fun relativeEnginePhrase(delta: Float, meterUnit: String): String {
  val absDelta = abs(delta)
  if (absDelta < 0.05f) return stringResource(Res.string.adj_preview_rel_hours_at)
  val formatted = formatEngineHours(absDelta)
  return if (delta > 0f) {
    stringResource(Res.string.adj_preview_rel_in_hours, formatted, meterUnit)
  } else {
    stringResource(Res.string.adj_preview_rel_over_hours, formatted, meterUnit)
  }
}

internal fun formatEngineHours(value: Float): String {
  // Show integers without trailing decimal, otherwise one decimal place.
  val rounded = (value * 10f).roundToInt() / 10f
  return if (rounded == rounded.toInt()
      .toFloat()
  ) rounded.toInt()
    .toString()
  else rounded.toString()
}

/**
 * Returns [text] as an AnnotatedString with each [fragment]'s first occurrence styled in
 * JetBrains Mono (via [WingslogTypography.dataMedium]). The surrounding text's fontSize is
 * preserved — only the font family and letter spacing are overridden.
 */
@Composable
internal fun monoOn(text: String, vararg fragments: String): AnnotatedString {
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
