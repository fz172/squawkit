package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.viewing.generated.resources.badge_due
import wingslog.feature.tasks.viewing.generated.resources.badge_not_due
import wingslog.feature.tasks.viewing.generated.resources.badge_overdue
import wingslog.feature.tasks.viewing.generated.resources.Res as ViewingRes

@Composable
internal fun taskDueStatusLabel(status: DueStatus): String = when (status) {
  DueStatus.OVERDUE -> stringResource(ViewingRes.string.badge_overdue)
  DueStatus.DUE_SOON -> stringResource(ViewingRes.string.badge_due)
  DueStatus.NORMAL -> stringResource(ViewingRes.string.badge_not_due)
  DueStatus.COMPLIED -> ""
}
