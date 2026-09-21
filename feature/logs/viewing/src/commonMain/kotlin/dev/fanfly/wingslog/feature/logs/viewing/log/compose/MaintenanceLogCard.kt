package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDayOfMonth
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.formatMeterNumber
import dev.fanfly.wingslog.core.template.primaryReading
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.taskNoun
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
  // The first meter this template declares that the log actually recorded. This used to switch on
  // `component_type` across three aviation fields, so a car's log matched nothing and showed a
  // blank where its odometer belonged (#761).
  val template = LocalThingTemplate.current
  val primary = template.primaryReading(log)

  // Filled rather than transparent so the swipe controls behind it do not show through.
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .clickable(onClick = onClick)
      // Min, so the spine can fill exactly the height the text asks for.
      .height(IntrinsicSize.Min)
      // No leading inset: the gutter lines up under the month header.
      .padding(end = Spacing.large),
  ) {
    // The gutter: the number alone. The unit is the same down the whole column, and the detail
    // sheet names it.
    Box(
      modifier = Modifier
        .width(rememberGutterWidth())
        .padding(top = Spacing.medium),
    ) {
      Text(
        text = primary?.let { template.formatMeterNumber(it.first.key, it.second) }.orEmpty(),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        // Pinned to the spine; a reading too long for the gutter grows into the screen's own
        // padding rather than pushing the dots out of line.
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentWidth(Alignment.End, unbounded = true),
      )
    }
    Spine(connectsUp = connectsUp, connectsDown = connectsDown, lit = isLatest)
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(vertical = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
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
}

/** The connector and this entry's dot, which sits level with the first line of text. */
@Composable
private fun Spine(connectsUp: Boolean, connectsDown: Boolean, lit: Boolean) {
  val line = MaterialTheme.colorScheme.outlineVariant
  val dot = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
  Canvas(
    modifier = Modifier
      .padding(end = Spacing.small)
      .width(Spacing.medium)
      .fillMaxHeight(),
  ) {
    val radius = size.width / 3
    val centre = Offset(size.width / 2, Spacing.medium.toPx() + Spacing.small.toPx() + radius / 2)
    val stroke = Spacing.hairline.toPx()
    if (connectsUp) drawLine(line, Offset(centre.x, 0f), centre, stroke)
    if (connectsDown) drawLine(line, centre, Offset(centre.x, size.height), stroke)
    drawCircle(dot, radius, centre)
  }
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

/** The widest reading the gutter holds without overflowing: a five-digit hour meter. */
private const val WIDEST_READING = "9999.9"

/**
 * Measured rather than a fixed dp, so [WIDEST_READING] fits whatever the font scale — plus a sliver
 * so the digits never touch the dot.
 */
@Composable
private fun rememberGutterWidth(): Dp {
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val style = WingslogTypography.dataSmall
  return remember(measurer, density, style) {
    with(density) { measurer.measure(WIDEST_READING, style, maxLines = 1).size.width.toDp() }
  } + Spacing.small
}

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
