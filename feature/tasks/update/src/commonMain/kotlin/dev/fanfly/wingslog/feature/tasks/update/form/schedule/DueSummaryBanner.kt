package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBanner
import dev.fanfly.wingslog.core.ui.common.compose.PreviewBannerTone
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_preview_primary_date
import wingslog.feature.tasks.update.generated.resources.adj_preview_primary_hours
import wingslog.feature.tasks.update.generated.resources.due_summary_schedule_alone_date
import wingslog.feature.tasks.update.generated.resources.due_summary_schedule_alone_meter
import wingslog.feature.tasks.update.generated.resources.schedule_preview_asap_primary
import wingslog.feature.tasks.update.generated.resources.schedule_preview_hint
import wingslog.feature.tasks.update.generated.resources.schedule_preview_label

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
  val (scheduleLine, scheduleHint, isEmpty) = scheduleSummary(
    schedule,
    linkedTaskName,
    meterUnit
  )
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
      monoOn(
        stringResource(Res.string.due_summary_schedule_alone_date, was),
        was
      )
    }

    naturalReading != null && naturalReading != dueReading -> {
      val was = formatEngineHours(naturalReading)
      monoOn(
        stringResource(
          Res.string.due_summary_schedule_alone_meter,
          was,
          meterUnit
        ), was
      )
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
