package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.common.compose.AdaptiveCardList
import dev.fanfly.wingslog.core.ui.common.compose.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.TaskCardItem
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.due_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.history_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.maintenance_tasks
import wingslog.feature.tasks.sharedassets.generated.resources.no_complied_yet
import wingslog.feature.tasks.sharedassets.generated.resources.no_tasks_yet
import wingslog.feature.tasks.sharedassets.generated.resources.no_tasks_yet_description
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

@Composable
fun ComplianceSection(
  activeTasks: List<MaintenanceTaskWithStatus>,
  completedTasks: List<MaintenanceTaskWithStatus>,
  showComplied: Boolean,
  onToggleComplied: (Boolean) -> Unit,
  onCardClick: (MaintenanceTaskWithStatus) -> Unit = {},
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    if (showHeader) {
      Text(
        text = stringResource(SharedRes.string.maintenance_tasks),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    DualSegmentedFilter(
      option1 = stringResource(
        SharedRes.string.due_with_count,
        activeTasks.size
      ),
      option2 = stringResource(
        SharedRes.string.history_with_count,
        completedTasks.size
      ),
      selectedIndex = if (showComplied) 1 else 0,
      onSelect = { onToggleComplied(it == 1) },
    )

    val displayList = if (showComplied) completedTasks else activeTasks

    if (displayList.isEmpty()) {
      if (!showComplied) {
        EmptyState(
          title = stringResource(SharedRes.string.no_tasks_yet),
          description = stringResource(SharedRes.string.no_tasks_yet_description),
          icon = Icons.Default.CheckCircle,
        )
      } else {
        Text(
          text = stringResource(SharedRes.string.no_complied_yet),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large)
        )
      }
    } else {
      AdaptiveCardList(
        items = displayList,
        columns = LocalLayoutTier.current.cardColumns,
        spacing = Spacing.medium,
      ) { item ->
        TaskCardItem(
          cardWithStatus = item,
          onClick = { onCardClick(item) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
