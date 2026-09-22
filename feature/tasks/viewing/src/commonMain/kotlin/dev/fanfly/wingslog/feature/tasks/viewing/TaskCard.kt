package dev.fanfly.wingslog.feature.tasks.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.core.ui.common.compose.ListRow
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.common.compose.highlightWords
import dev.fanfly.wingslog.core.ui.common.compose.searchHighlightStyle
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueStatus

@Composable
fun TaskCard(
  title: String,
  subtitle: String,
  statusLabel: String,
  statusValue: String,
  badgeText: String,
  icon: ImageVector,
  statusColor: Color,
  dueStatus: DueStatus = DueStatus.NORMAL,
  onClick: () -> Unit = {},
  modifier: Modifier = Modifier,
  /** Words the active search matched, highlighted where they appear. */
  highlight: Set<String> = emptySet(),
  /** A match the card cannot otherwise show, e.g. a reference number. */
  matchNote: AnnotatedString? = null,
) {
  val highlightStyle = searchHighlightStyle()
  val badgeTier = when (dueStatus) {
    DueStatus.OVERDUE -> StatusTier.CRITICAL
    DueStatus.DUE_SOON -> StatusTier.CAUTION
    DueStatus.COMPLIED -> StatusTier.POSITIVE
    DueStatus.NORMAL -> StatusTier.NEUTRAL
  }

  // The deadline and the notes share the metadata line: the label sits with its value as one
  // phrase, where a caption on its own row cost the card a divider and two lines.
  val metadata = buildAnnotatedString {
    if (statusValue.isNotBlank()) {
      append(listOf(statusLabel, statusValue).filter { it.isNotBlank() }
               .joinToString(" "))
    }
    if (subtitle.isNotBlank()) {
      if (length > 0) append(" · ")
      append(highlightWords(subtitle, highlight, highlightStyle))
    }
  }

  ListRow(
    title = highlightWords(title, highlight, highlightStyle),
    metadata = metadata.takeIf { it.isNotEmpty() },
    onClick = onClick,
    modifier = modifier,
    leading = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(Spacing.extraLarge),
        // A normal task has nothing to say, so its icon stays neutral; every other state earns
        // its colour, which used to live on the deadline line the metadata now absorbs.
        tint = if (dueStatus == DueStatus.NORMAL) MaterialTheme.colorScheme.onSurfaceVariant
        else statusColor,
      )
    },
    trailing = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (badgeText.isNotBlank()) StatusChip(
          label = badgeText,
          tier = badgeTier
        )
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

@Preview
@Composable
fun PreviewTaskCard() = TaskCard(
  title = "100 Hr Inspection",
  subtitle = "Routine engine and airframe check",
  statusLabel = "Deadline",
  statusValue = "05/13/2026",
  badgeText = "OVERDUE",
  icon = Icons.Default.Schedule,
  statusColor = MaterialTheme.statusColors.critical.accent,
  dueStatus = DueStatus.OVERDUE,
  modifier = Modifier.fillMaxWidth()
)
