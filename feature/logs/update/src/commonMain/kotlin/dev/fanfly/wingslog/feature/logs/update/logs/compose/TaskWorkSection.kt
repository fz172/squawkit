package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.add
import wingslog.core.ui.generated.resources.remove
import wingslog.feature.tasks.sharedassets.generated.resources.Res
import wingslog.feature.tasks.sharedassets.generated.resources.maintenance_tasks
import wingslog.feature.tasks.sharedassets.generated.resources.no_task_work_recorded
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_task
import wingslog.core.ui.generated.resources.Res as CoreRes

@Composable
fun TaskWorkSection(
  selectedIds: List<String>,
  availableCards: List<MaintenanceTask>,
  onAddClick: () -> Unit,
  onRemove: (cardId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(Res.string.maintenance_tasks),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      OutlinedButton(
        onClick = onAddClick,
        contentPadding = PaddingValues(
          horizontal = 12.dp, vertical = 4.dp
        ),
      ) {
        Icon(
          Icons.Default.Add, contentDescription = null, modifier = Modifier.width(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
          stringResource(CoreRes.string.add), style = MaterialTheme.typography.labelMedium
        )
      }
    }

    if (selectedIds.isEmpty()) {
      Text(
        text = stringResource(Res.string.no_task_work_recorded),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      selectedIds.forEach { cardId ->
        val card = availableCards.firstOrNull { it.id == cardId }
        val title = card?.title ?: stringResource(Res.string.unknown_task, cardId)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { onRemove(cardId) }) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(CoreRes.string.remove),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        HorizontalDivider()
      }
    }
  }
}