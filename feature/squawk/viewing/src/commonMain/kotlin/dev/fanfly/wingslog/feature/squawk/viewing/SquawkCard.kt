package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.ui.common.compose.ListRow
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.common.compose.highlightWords
import dev.fanfly.wingslog.core.ui.common.compose.searchHighlightStyle
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.thing.SquawkPriority
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_status_addressed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_status_dismissed

@Composable
fun SquawkCard(
  item: SquawkWithStatus,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** False under a tier header, which already names the priority. */
  showPriority: Boolean = true,
  /** Words the active search matched, highlighted where they appear. */
  highlight: Set<String> = emptySet(),
  /** A match the card cannot otherwise show, e.g. a serial. */
  matchNote: AnnotatedString? = null,
) {
  val squawk = item.squawk
  val highlightStyle = searchHighlightStyle()
  // The down-state defect is set apart by the tone of its title, not by a box around the row.
  val titleColor = if (squawk.priority == SquawkPriority.SQUAWK_PRIORITY_AOG) {
    MaterialTheme.statusColors.blocking.accent
  } else {
    Color.Unspecified
  }

  val raisedOn = squawk.created_at
    ?.takeIf { it.getEpochSecond() > 0L }
    ?.toLocalDate()
    ?.toDisplayFormat()
  val metadata = buildAnnotatedString {
    if (raisedOn != null) append(raisedOn)
    if (squawk.description.isNotBlank()) {
      if (length > 0) append(" · ")
      append(highlightWords(squawk.description, highlight, highlightStyle))
    }
  }

  ListRow(
    title = buildAnnotatedString {
      withStyle(SpanStyle(color = titleColor)) {
        append(highlightWords(squawk.title, highlight, highlightStyle))
      }
    },
    metadata = metadata.takeIf { it.isNotEmpty() },
    onClick = onClick,
    modifier = modifier,
    leading = if (showPriority) ({ PriorityBadge(item) }) else null,
    trailing = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusBadge(item.status)
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    supporting = matchNote?.let {
      {
        Text(
          text = it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

@Composable
private fun StatusBadge(status: SquawkStatus) {
  val (tier, label) = when (status) {
    SquawkStatus.ADDRESSED -> Pair(
      StatusTier.POSITIVE,
      stringResource(Res.string.squawk_status_addressed),
    )

    SquawkStatus.DISMISSED -> Pair(
      StatusTier.NEUTRAL,
      stringResource(Res.string.squawk_status_dismissed),
    )

    SquawkStatus.OPEN -> return
  }
  StatusChip(label = label, tier = tier)
}

@Composable
internal fun PriorityBadge(item: SquawkWithStatus) {
  val priority = item.squawk.priority
  val tier = priority.statusTier()
  val label = when (priority) {
    SquawkPriority.SQUAWK_PRIORITY_AOG -> LexiconFormatter.titleCase(
      LocalThingLexicon.current.down_status
    )

    SquawkPriority.SQUAWK_PRIORITY_HIGH -> stringResource(Res.string.priority_high)
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> stringResource(Res.string.priority_medium)
    else -> stringResource(Res.string.priority_low)
  }
  StatusChip(label = label, tier = tier)
}

private fun SquawkPriority.statusTier(): StatusTier = when (this) {
  SquawkPriority.SQUAWK_PRIORITY_AOG -> StatusTier.BLOCKING
  SquawkPriority.SQUAWK_PRIORITY_HIGH -> StatusTier.CRITICAL
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> StatusTier.CAUTION
  else -> StatusTier.NEUTRAL
}
