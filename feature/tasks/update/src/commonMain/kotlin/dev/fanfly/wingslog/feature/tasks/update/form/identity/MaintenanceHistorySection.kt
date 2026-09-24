package dev.fanfly.wingslog.feature.tasks.update.form.identity

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.form.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.no_log_history
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun MaintenanceHistorySection(
  taskId: String,
  availableLogs: List<MaintenanceLog>,
  onAddLog: () -> Unit,
  onRemoveLog: (MaintenanceLog) -> Unit,
) {
  val linkedLogs = remember(availableLogs, taskId) {
    availableLogs
      .filter { taskId in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      FormSectionLabel(LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.logNoun))
      OutlinedButton(
        onClick = onAddLog,
        contentPadding = PaddingValues(
          horizontal = Spacing.medium,
          vertical = Spacing.extraSmall,
        ),
      ) {
        Icon(
          Icons.Default.Add,
          contentDescription = null,
          modifier = Modifier.width(Spacing.large),
        )
        Spacer(Modifier.width(Spacing.extraSmall))
        Text(
          stringResource(CoreRes.string.add),
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }

    if (linkedLogs.isEmpty()) {
      Text(
        text = stringResource(
          Res.string.no_log_history,
          LocalThingLexicon.current.logNoun.plural,
          LocalThingLexicon.current.taskNoun.singular,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      linkedLogs.forEach { log ->
        val displayText = log.work_description.ifBlank { log.id }
        val logDate = log.timestamp
          ?.takeIf { it.getEpochSecond() > 0L }
          ?.toLocalDate()
          ?.toDisplayFormat()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = displayText,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRemoveLog(log) }) {
              Icon(
                Icons.Default.Close,
                contentDescription = stringResource(CoreRes.string.remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (logDate != null) {
            Text(
              text = logDate,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        HorizontalDivider()
      }
    }
  }
}
