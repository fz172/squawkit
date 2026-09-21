package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDayOfMonth
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.formatMeterNumber
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.timelineReading
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.common.compose.TimelineGapRow
import dev.fanfly.wingslog.core.ui.common.compose.TimelineRow
import dev.fanfly.wingslog.core.ui.common.compose.highlightWords
import dev.fanfly.wingslog.core.ui.common.compose.searchHighlightStyle
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Technician
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.log_file_count_one
import wingslog.feature.logs.viewing.generated.resources.log_gap_one
import wingslog.feature.logs.viewing.generated.resources.log_gap_plural
import wingslog.feature.logs.viewing.generated.resources.log_file_count_plural
import wingslog.feature.logs.viewing.generated.resources.log_squawk_count_one
import wingslog.feature.logs.viewing.generated.resources.log_squawk_count_plural
import wingslog.feature.logs.viewing.generated.resources.log_task_count_one
import wingslog.feature.logs.viewing.generated.resources.log_task_count_plural
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import kotlin.time.Instant
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

/**
 * One work log on the spine: the meter reading in the gutter, a dot on the connector, a one-line
 * summary and a metadata line. The full description is the detail sheet's job.
 */
@Composable
fun MaintenanceLogCard(
  log: MaintenanceLog,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** Whether the spine reaches the entry directly above / below this one. */
  connectsUp: Boolean = false,
  connectsDown: Boolean = false,
  /** The newest log, whose dot is lit. */
  isLatest: Boolean = false,
  /** Words the active search matched, highlighted where they appear. */
  highlight: Set<String> = emptySet(),
  /** A match the card cannot otherwise show, e.g. a serial. */
  matchNote: AnnotatedString? = null,
) {
  val template = LocalThingTemplate.current
  // One meter down the whole gutter, so the column reads as a series; the detail sheet leads with
  // the component's own.
  val primary = template.timelineReading(log)

  TimelineRow(
    // The number alone: the meter is the same down the whole column, and the detail sheet names it.
    gutter = primary?.let { template.formatMeterNumber(it.first.key, it.second) }.orEmpty(),
    modifier = modifier,
    connectsUp = connectsUp,
    connectsDown = connectsDown,
    lit = isLatest,
    onClick = onClick,
  ) {
    Text(
      text = highlightWords(log.work_description.asSummaryLine(), highlight, searchHighlightStyle()),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = log.metadataLine(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    matchNote?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The logs a filter removed between two entries, counted in the template's own noun. */
@Composable
fun LogGapRow(omitted: Int, modifier: Modifier = Modifier) {
  val lexicon = LocalThingLexicon.current
  TimelineGapRow(
    text = if (omitted == 1) {
      stringResource(MaintenanceRes.string.log_gap_one, lexicon.logNoun.singular)
    } else {
      stringResource(MaintenanceRes.string.log_gap_plural, omitted, lexicon.logNoun.plural)
    },
    modifier = modifier,
  )
}

/** "Sep 5 · J. Rivera · 1 task · 5 files" — whichever of them this log has. */
@Composable
private fun MaintenanceLog.metadataLine(): String {
  val lexicon = LocalThingLexicon.current
  val date = timestamp?.toLocalDate()
    ?.toDayOfMonth()
    ?: stringResource(SharedRes.string.unknown_date)
  val taskCount = inspection_ids.size
  val squawkCount = squawk_ids.size
  val fileCount = attachments.size
  return listOfNotNull(
    date,
    technician?.name?.takeIf { it.isNotBlank() },
    when {
      taskCount == 1 ->
        stringResource(MaintenanceRes.string.log_task_count_one, lexicon.taskNoun.singular)

      taskCount > 1 -> stringResource(
        MaintenanceRes.string.log_task_count_plural,
        taskCount,
        lexicon.taskNoun.plural,
      )

      else -> null
    },
    when {
      squawkCount == 1 ->
        stringResource(MaintenanceRes.string.log_squawk_count_one, lexicon.squawkNoun.singular)

      squawkCount > 1 -> stringResource(
        MaintenanceRes.string.log_squawk_count_plural,
        squawkCount,
        lexicon.squawkNoun.plural,
      )

      else -> null
    },
    // A count, never thumbnails: the list says a log has files, the detail sheet shows them.
    when {
      fileCount == 1 -> stringResource(MaintenanceRes.string.log_file_count_one)
      fileCount > 1 -> stringResource(MaintenanceRes.string.log_file_count_plural, fileCount)
      else -> null
    },
  ).joinToString(" · ")
}

private val WHITESPACE_RUN = Regex("\\s+")

/** A stored description as one run of text: newlines and blank lines would cost the row its one line. */
private fun String.asSummaryLine(): String = replace(WHITESPACE_RUN, " ").trim()

private data class BadgeScheme(
  val background: Color,
  val contentColor: Color,
)

@Composable
private fun badgeSchemeFor(type: ComponentType): BadgeScheme = when (type) {
  ComponentType.COMPONENT_ENGINE -> BadgeScheme(
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.onPrimaryContainer,
  )

  ComponentType.COMPONENT_AIRFRAME -> BadgeScheme(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurfaceVariant,
  )

  ComponentType.COMPONENT_PROPELLER -> BadgeScheme(
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.onSecondaryContainer,
  )

  else -> BadgeScheme(
    MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The component pill, or nothing when the type describes nothing the user picked.
 *
 * The form defaults to `COMPONENT_AIRFRAME` and stamped it on even where the picker never appeared,
 * so a car's every log wore an "Airframe" pill.
 */
@Composable
internal fun LogComponentBadge(
  type: ComponentType,
  modifier: Modifier = Modifier,
) {
  if (!componentTypesApply || type == ComponentType.COMPONENT_UNKNOWN) return
  ComponentTypeBadge(type, modifier)
}

@Composable
internal fun ComponentTypeBadge(
  type: ComponentType,
  modifier: Modifier = Modifier,
) {
  val scheme = badgeSchemeFor(type)
  Box(
    modifier = modifier
      .background(
        color = scheme.background,
        shape = RoundedCornerShape(Spacing.badgeCornerRadius)
      )
      .padding(
        horizontal = Spacing.small,
        vertical = Spacing.extraSmall
      ),
  ) {
    Text(
      text = type.displayName()
        .uppercase(),
      color = scheme.contentColor,
      fontSize = 10.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.6.sp,
      lineHeight = 14.sp,
    )
  }
}

@Preview
@Composable
private fun PreviewMaintenanceLogCard() {
  MaintenanceLogCard(
    log = MaintenanceLog(
      id = "preview-1",
      timestamp = Instant.fromEpochSeconds(1_745_000_000)
        .toWireInstant(),
      work_description = "Replaced left magneto per SB-1234. Performed mag drop check — within limits.",
      component_type = ComponentType.COMPONENT_ENGINE,
      readings = listOf(MeterReading(MeterKeys.ENGINE_HOURS, value_ = 1432.5)),
      inspection_ids = listOf(
        "insp-1",
        "insp-2"
      ),
      technician = Technician(
        id = "tech-1",
        name = "J. Rivera"
      ),
    ),
    onClick = {},
  )
}
