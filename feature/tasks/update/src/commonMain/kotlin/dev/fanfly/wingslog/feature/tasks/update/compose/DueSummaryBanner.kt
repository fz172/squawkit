package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBanner
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBannerTone
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_preview_primary_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_primary_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_due_today
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_hours_at
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_day
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_days
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_in_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_over_hours
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_overdue_day
import wingslog.feature.tasks.update.generated.resources.adj_preview_rel_overdue_days
import wingslog.feature.tasks.update.generated.resources.due_summary_schedule_alone_date
import wingslog.feature.tasks.update.generated.resources.due_summary_schedule_alone_meter
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
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_months_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_set_secondary
import wingslog.feature.tasks.update.generated.resources.schedule_unit_days
import wingslog.feature.tasks.update.generated.resources.schedule_unit_months
import wingslog.feature.tasks.update.generated.resources.schedule_unit_years

/**
 * The one banner the schedule and adjustments tabs both show, so the same task reads the same way
 * on either: **when it is next due**, then **the schedule** that produces that, then **the
 * override** if one is in force. The two tabs used to keep separate banners — one describing the
 * rule, one describing the date — and a reader flipping between them had to reconcile them.
 *
 * [effectiveDue] is the draft as it would be saved, override included; [naturalDue] is the same
 * draft with every override stripped, which is what the third line reports the schedule would say
 * on its own. Both come from the due engine over the Thing's real logs, so the banner never
 * disagrees with the card the dashboard will draw.
 */
@Composable
internal fun DueSummaryBanner(
  schedule: ScheduleState,
  linkedTaskName: String?,
  meterUnit: String,
  overrideOn: Boolean,
  effectiveDue: DueMetadata?,
  naturalDue: DueMetadata?,
  currentReading: (String) -> Float,
) {
  val (scheduleLine, scheduleHint, isEmpty) = scheduleSummary(schedule, linkedTaskName, meterUnit)
  val today = Clock.System.now()
    .toLocalDateTime(TimeZone.currentSystemDefault()).date

  val dueDate = effectiveDue?.nextDueDate
  val dueReading = effectiveDue?.nextDueEngine
  val dueLine: AnnotatedString? = when {
    effectiveDue == null -> null
    effectiveDue.isImmediate -> AnnotatedString(stringResource(Res.string.schedule_preview_asap_primary))
    dueDate != null -> {
      val dateStr = dueDate.toDisplayFormat()
      monoOn(
        stringResource(
          Res.string.adj_preview_primary_date,
          dateStr,
          relativeDaysPhrase(today.daysUntil(dueDate)),
        ),
        dateStr,
      )
    }

    dueReading != null -> {
      val dueStr = formatEngineHours(dueReading)
      val current = effectiveDue.nextDueMeterKey?.let(currentReading) ?: 0f
      monoOn(
        stringResource(
          Res.string.adj_preview_primary_hours,
          dueStr,
          meterUnit,
          relativeEnginePhrase(dueReading - current, meterUnit),
        ),
        dueStr,
      )
    }

    else -> null
  }

  // Only worth a line when it changes something: the same date twice says nothing.
  val naturalDate = naturalDue?.nextDueDate
  val naturalReading = naturalDue?.nextDueEngine
  val overrideLine: AnnotatedString? = when {
    !overrideOn -> null
    naturalDate != null && naturalDate != dueDate -> {
      val was = naturalDate.toDisplayFormat()
      monoOn(stringResource(Res.string.due_summary_schedule_alone_date, was), was)
    }

    naturalReading != null && naturalReading != dueReading -> {
      val was = formatEngineHours(naturalReading)
      monoOn(stringResource(Res.string.due_summary_schedule_alone_meter, was, meterUnit), was)
    }

    else -> null
  }

  PreviewBanner(
    label = stringResource(Res.string.schedule_preview_label),
    hint = stringResource(Res.string.schedule_preview_hint),
    // With a computable due it leads and the schedule explains it; without one the schedule
    // (or the prompt to set one) is all there is to say.
    primary = dueLine ?: AnnotatedString(scheduleLine),
    secondary = AnnotatedString(if (dueLine != null) scheduleLine else scheduleHint),
    tertiary = overrideLine,
    tone = if (isEmpty) PreviewBannerTone.Neutral else PreviewBannerTone.Active,
  )
}

/** The schedule in one sentence, a hint for the second line, and whether there is one at all. */
@Composable
internal fun scheduleSummary(
  state: ScheduleState,
  linkedTaskName: String?,
  meterUnit: String,
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
      stringResource(Res.string.schedule_preview_linked_primary, linkedTaskName),
      secondary,
      false,
    )
  }
  if (state.mode == ScheduleMode.SEASONAL) {
    if (state.seasonalMonths.isEmpty()) {
      return Triple(
        stringResource(Res.string.schedule_preview_set_months_primary),
        stringResource(Res.string.schedule_preview_set_secondary),
        false,
      )
    }
    val months = formatMonthList(state.seasonalMonths)
    return if (state.recurrence == ScheduleRecurrence.ONE_TIME) {
      Triple(
        stringResource(Res.string.schedule_preview_due_seasonal_once, months),
        stringResource(Res.string.schedule_preview_one_time_secondary),
        false,
      )
    } else {
      Triple(
        stringResource(Res.string.schedule_preview_due_seasonal, months),
        stringResource(Res.string.schedule_preview_recurring_secondary),
        false,
      )
    }
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
    return Triple(stringResource(primaryRes, n, unitStr), stringResource(secondaryRes), false)
  }
  // HOURS
  if (state.hourValue.isBlank()) {
    return Triple(
      stringResource(Res.string.schedule_preview_set_hours_primary, meterUnit),
      stringResource(Res.string.schedule_preview_set_secondary),
      false,
    )
  }
  val primaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
    Res.string.schedule_preview_due_in_hours else Res.string.schedule_preview_due_every_hours
  val secondaryRes = if (state.recurrence == ScheduleRecurrence.ONE_TIME)
    Res.string.schedule_preview_one_time_secondary else Res.string.schedule_preview_recurring_secondary
  return Triple(
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
private fun relativeDaysPhrase(days: Int): String = when {
  days == 0 -> stringResource(Res.string.adj_preview_rel_due_today)
  days == 1 -> stringResource(Res.string.adj_preview_rel_in_day)
  days > 1 -> stringResource(Res.string.adj_preview_rel_in_days, days)
  days == -1 -> stringResource(Res.string.adj_preview_rel_overdue_day)
  else -> stringResource(Res.string.adj_preview_rel_overdue_days, -days)
}

@Composable
private fun relativeEnginePhrase(delta: Float, meterUnit: String): String {
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
