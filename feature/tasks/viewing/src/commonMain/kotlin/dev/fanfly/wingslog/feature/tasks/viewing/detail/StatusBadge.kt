package dev.fanfly.wingslog.feature.tasks.viewing.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.viewing.generated.resources.badge_overdue
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes

@Composable
internal fun StatusBadge(dueStatus: DueMetadata) {
  val (label, tier) = when (dueStatus.status) {
    DueStatus.OVERDUE -> Pair(
      stringResource(ViewingRes.string.badge_overdue),
      StatusTier.CRITICAL,
    )

    DueStatus.DUE_SOON -> Pair(
      LexiconFormatter.titleCase(LocalThingLexicon.current.due_status),
      StatusTier.CAUTION,
    )

    else -> return
  }
  StatusChip(label = label, tier = tier)
}

@Composable
internal fun dueStatusColor(status: DueStatus): Color = when (status) {
  DueStatus.OVERDUE -> MaterialTheme.statusColors.critical.accent
  DueStatus.DUE_SOON -> MaterialTheme.statusColors.caution.accent
  DueStatus.COMPLIED -> MaterialTheme.statusColors.positive.accent
  DueStatus.NORMAL -> MaterialTheme.statusColors.positive.accent
}
