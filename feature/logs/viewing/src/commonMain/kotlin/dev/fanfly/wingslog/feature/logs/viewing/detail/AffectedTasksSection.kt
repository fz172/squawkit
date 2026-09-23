package dev.fanfly.wingslog.feature.logs.viewing.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.affected_maintenance_tasks
import wingslog.feature.logs.viewing.generated.resources.no_tasks_linked
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_task
import wingslog.feature.logs.viewing.generated.resources.Res as ViewingRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedTaskRes

@Composable
internal fun AffectedTasksSection(
  log: MaintenanceLog,
  availableCards: List<MaintenanceTask>,
  onTaskClick: ((String) -> Unit)?,
) {
  Text(
    text = stringResource(
      ViewingRes.string.affected_maintenance_tasks,
      LexiconFormatter.titleCasePlural(LocalThingLexicon.current.taskNoun),
    ),
    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onSurface,
  )

  if (log.inspection_ids.isEmpty()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(Spacing.chipCornerRadius),
        )
        .padding(Spacing.medium),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(
          ViewingRes.string.no_tasks_linked,
          LocalThingLexicon.current.taskNoun.plural,
          LocalThingLexicon.current.logNoun.singular,
        ),
        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      log.inspection_ids.forEach { cardId ->
        val card = availableCards.find { it.id == cardId }
        val title = card?.title ?: stringResource(
          SharedTaskRes.string.unknown_task,
          cardId,
          LocalThingLexicon.current.taskNoun.singular,
        )
        LinkedEntityRow(
          title = title,
          icon = Icons.Default.Build,
          onClick = onTaskClick?.let { cb -> { cb(cardId) } },
        )
      }
    }
  }
}
