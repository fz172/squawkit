package dev.fanfly.wingslog.feature.thing.dashboard.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.overviewLogEmptyHint
import dev.fanfly.wingslog.core.template.overviewLogEmptyTitle
import dev.fanfly.wingslog.core.template.overviewSquawkEmptyHint
import dev.fanfly.wingslog.core.template.overviewTaskEmptyHint
import dev.fanfly.wingslog.core.template.overviewTaskEmptyTitle
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.TaskCardItem
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.all
import wingslog.feature.squawk.sharedassets.generated.resources.no_open_squawks
import wingslog.feature.thing.dashboard.generated.resources.Res
import wingslog.feature.thing.dashboard.generated.resources.overview_all_logs
import wingslog.feature.thing.dashboard.generated.resources.overview_next_due
import wingslog.feature.thing.dashboard.generated.resources.overview_open_squawks
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes

@Composable
internal fun DashboardLowerGrid(
  state: ThingOverviewUiState.Success,
  onTaskClick: (MaintenanceTaskWithStatus) -> Unit,
  onLogsClick: () -> Unit,
  onViewSquawksClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Overdue and due-soon tasks are already listed under Needs attention, above.
  val nextTask = state.activeTasks.firstOrNull { !it.needsAttention }
  val openSquawks = state.squawks.filter { it.status == SquawkStatus.OPEN }
    .sortedByDescending { it.squawk.created_at?.getEpochSecond() ?: 0L }

  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    RailCard(
      title = workLogsTitle(state),
      actionLabel = stringResource(Res.string.overview_all_logs),
      onActionClick = onLogsClick,
      modifier = Modifier.weight(1f),
    ) {
      if (state.recentLogs.isEmpty()) {
        EmptyRailState(
          icon = Icons.Default.Description,
          title = LocalThingLexicon.current.overviewLogEmptyTitle,
          body = LocalThingLexicon.current.overviewLogEmptyHint,
        )
      } else {
        state.recentLogs.forEachIndexed { index, log ->
          RecentLogRow(log = log, onClick = onLogsClick)
          if (index < state.recentLogs.lastIndex) {
            HorizontalDivider(
              color = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.55f
              )
            )
          }
        }
      }
    }

    Column(
      modifier = Modifier.width(320.dp),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      if (nextTask != null) {
        RailCard(title = stringResource(Res.string.overview_next_due)) {
          TaskCardItem(
            cardWithStatus = nextTask,
            onClick = { onTaskClick(nextTask) },
          )
        }
      } else {
        RailCard(title = stringResource(Res.string.overview_next_due)) {
          EmptyRailState(
            icon = Icons.Default.CheckCircle,
            title = LocalThingLexicon.current.overviewTaskEmptyTitle,
            body = LocalThingLexicon.current.overviewTaskEmptyHint,
          )
        }
      }

      RailCard(
        title = stringResource(
          Res.string.overview_open_squawks,
          LocalThingLexicon.current.squawkNoun.plural,
        ),
        actionLabel = stringResource(CoreRes.string.all),
        onActionClick = onViewSquawksClick
      ) {
        if (openSquawks.isEmpty()) {
          EmptyRailState(
            icon = Icons.Default.CheckCircle,
            title = stringResource(
              SquawkRes.string.no_open_squawks,
              LocalThingLexicon.current.squawkNoun.plural,
            ),
            body = LocalThingLexicon.current.overviewSquawkEmptyHint,
          )
        } else {
          val previewSquawks = openSquawks.take(3)
          previewSquawks.forEachIndexed { index, item ->
            SquawkRailRow(
              title = item.squawk.title,
              subtitle = item.squawk.description.ifBlank {
                item.squawk.created_at?.toLocalDate()
                  ?.toDisplayFormat()
                  ?: ""
              },
              onClick = onViewSquawksClick,
            )
            if (index < previewSquawks.lastIndex) {
              HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(
                  alpha = 0.55f
                )
              )
            }
          }
        }
      }
    }
  }
}
