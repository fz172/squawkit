package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.MaintenanceTask
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.tasks.sharedassets.generated.resources.Res
import wingslog.feature.tasks.sharedassets.generated.resources.no_task_work_recorded
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_task
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
fun TaskWorkSection(
  selectedIds: List<String>,
  availableCards: List<MaintenanceTask>,
  onRemove: (cardId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    if (selectedIds.isEmpty()) {
      Text(
        text = stringResource(
          Res.string.no_task_work_recorded,
          LocalThingLexicon.current.taskNoun.plural,
          LocalThingLexicon.current.logNoun.singular,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      selectedIds.forEach { cardId ->
        val card = availableCards.firstOrNull { it.id == cardId }
        val title = card?.title ?: stringResource(
          Res.string.unknown_task,
          cardId,
          LocalThingLexicon.current.taskNoun.singular,
        )
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
      }
    }
  }
}
