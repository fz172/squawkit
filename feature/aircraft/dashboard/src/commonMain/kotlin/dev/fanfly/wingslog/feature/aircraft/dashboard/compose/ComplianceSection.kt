package dev.fanfly.wingslog.feature.aircraft.dashboard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.TaskCardItem
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.tasks.sharedassets.generated.resources.add_task
import wingslog.feature.tasks.sharedassets.generated.resources.due_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.history_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.maintenance_tasks
import wingslog.feature.tasks.sharedassets.generated.resources.no_complied_yet
import wingslog.feature.tasks.sharedassets.generated.resources.no_tasks_yet

@Composable
fun ComplianceSection(
  activeTasks: List<MaintenanceTaskWithStatus>,
  completedTasks: List<MaintenanceTaskWithStatus>,
  showComplied: Boolean,
  onToggleComplied: (Boolean) -> Unit,
  onAddClick: () -> Unit,
  onCardClick: (MaintenanceTaskWithStatus) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    Text(
      text = stringResource(SharedRes.string.maintenance_tasks),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )

    SingleChoiceSegmentedButtonRow(
      modifier = Modifier.fillMaxWidth()
    ) {
      SegmentedButton(
        selected = !showComplied,
        onClick = { onToggleComplied(false) },
        shape = SegmentedButtonDefaults.itemShape(
          index = 0,
          count = 2
        )
      ) {
        Text(
          stringResource(
            SharedRes.string.due_with_count,
            activeTasks.size
          )
        )
      }
      SegmentedButton(
        selected = showComplied,
        onClick = { onToggleComplied(true) },
        shape = SegmentedButtonDefaults.itemShape(
          index = 1,
          count = 2
        )
      ) {
        Text(
          stringResource(
            SharedRes.string.history_with_count,
            completedTasks.size
          )
        )
      }
    }

    val displayList = if (showComplied) completedTasks else activeTasks

    if (displayList.isEmpty()) {
      if (!showComplied) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Spacing.cardCornerRadius),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
        ) {
          Column(
            modifier = Modifier.padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
          ) {
            Text(
              text = stringResource(SharedRes.string.no_tasks_yet),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = onAddClick,
              shape = RoundedCornerShape(Spacing.buttonCornerRadius)
            ) {
              Icon(
                Icons.Default.Add,
                contentDescription = null
              )
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(SharedRes.string.add_task).uppercase())
            }
          }
        }
      } else {
        Text(
          text = stringResource(SharedRes.string.no_complied_yet),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large)
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        displayList.forEach { item ->
          TaskCardItem(
            cardWithStatus = item,
            onClick = { onCardClick(item) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
