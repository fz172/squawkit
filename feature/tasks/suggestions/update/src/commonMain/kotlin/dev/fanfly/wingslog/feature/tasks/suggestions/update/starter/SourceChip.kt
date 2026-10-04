package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_open_document
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.TaskSourceKind
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_source_common_practice
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_source_document
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_source_logs
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_source_manufacturer
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_source_page
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_source_verify

/**
 * The card's source chip (PRD R17, R26): what the suggestion rests on, as text, never a color.
 * Nothing for a card with no source kind (the app's own pack, in production).
 */
@Composable
fun SourceChip(suggestion: TaskSuggestion, onClick: () -> Unit) {
  val label = suggestion.source_kind.label() ?: return
  SuggestionChip(onClick = onClick, label = { Text(label) })
}

/**
 * Tapping the chip: the full citation and page, the one-line rationale, and for a manufacturer
 * schedule the model only recalled, a reminder to check the manual (R17).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(
  suggestion: TaskSuggestion,
  onDismiss: () -> Unit,
  /** Opens the cited document, at the cited page where the platform can (PRD R30); null without one. */
  onOpenDocument: (() -> Unit)? = null,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      suggestion.source_kind.label()
        ?.let {
          Text(text = it, style = MaterialTheme.typography.titleMedium)
        }
      suggestion.citation.takeIf { it.isNotBlank() }
        ?.let {
          Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
      suggestion.page_ref.takeIf { it.isNotBlank() }
        ?.let {
          Text(
            text = stringResource(Res.string.suggestion_source_page, it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      if (onOpenDocument != null) {
        TextButton(onClick = onOpenDocument) {
          Text(stringResource(Res.string.suggestion_open_document))
        }
      }
      suggestion.rationale.takeIf { it.isNotBlank() }
        ?.let {
          Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
      if (suggestion.source_kind == TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE) {
        Text(
          text = stringResource(Res.string.suggestion_source_verify),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TaskSourceKind.label(): String? = when (this) {
  TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT -> stringResource(Res.string.suggestion_source_document)
  TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE -> stringResource(Res.string.suggestion_source_manufacturer)
  TaskSourceKind.TASK_SOURCE_KIND_COMMON_PRACTICE -> stringResource(Res.string.suggestion_source_common_practice)
  TaskSourceKind.TASK_SOURCE_KIND_LOGS -> stringResource(Res.string.suggestion_source_logs)
  TaskSourceKind.TASK_SOURCE_KIND_UNSPECIFIED -> null
}
