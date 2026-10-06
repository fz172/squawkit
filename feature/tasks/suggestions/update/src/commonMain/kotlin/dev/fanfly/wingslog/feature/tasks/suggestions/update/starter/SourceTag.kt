package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_tag_document
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_tag_logs
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_tag_manufacturer

/**
 * The row's source tag (PRD R17, R26; 1d): what the suggestion rests on, in words, with the page
 * where there is one ("MANUAL · P. 42"). A schedule from a document or the manufacturer is filled,
 * common practice and the logs outlined, so the stronger sources stand out without a new color.
 * Nothing for a suggestion with no source kind. Tapping it shows the full citation.
 */
@Composable
internal fun SourceTag(suggestion: TaskSuggestion, onClick: () -> Unit) {
  val label = suggestion.source_kind.tag() ?: return
  val text = listOf(label, suggestion.page_ref.trim())
    .filter { it.isNotEmpty() }
    .joinToString(" · ")
    .uppercase()
  val filled = suggestion.source_kind == TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT ||
    suggestion.source_kind == TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE
  val shape = RoundedCornerShape(Spacing.badgeCornerRadius)
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = if (filled) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    modifier = Modifier
      .clip(shape)
      .then(
        if (filled) {
          Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
        } else {
          Modifier.border(Spacing.hairline, MaterialTheme.colorScheme.outline, shape)
        },
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

/**
 * Tapping the chip: the full citation and page, the one-line rationale, and for a manufacturer
 * schedule the model only recalled, a reminder to check the manual (R17).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(suggestion: TaskSuggestion, onDismiss: () -> Unit) {
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

/** The tag's short word for where a suggestion comes from. */
@Composable
private fun TaskSourceKind.tag(): String? = when (this) {
  TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT -> stringResource(Res.string.suggestion_tag_document)
  TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE ->
    stringResource(Res.string.suggestion_tag_manufacturer)

  TaskSourceKind.TASK_SOURCE_KIND_COMMON_PRACTICE ->
    stringResource(Res.string.suggestion_source_common_practice)

  TaskSourceKind.TASK_SOURCE_KIND_LOGS -> stringResource(Res.string.suggestion_tag_logs)
  TaskSourceKind.TASK_SOURCE_KIND_UNSPECIFIED -> null
}

@Composable
private fun TaskSourceKind.label(): String? = when (this) {
  TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT -> stringResource(Res.string.suggestion_source_document)
  TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE -> stringResource(Res.string.suggestion_source_manufacturer)
  TaskSourceKind.TASK_SOURCE_KIND_COMMON_PRACTICE -> stringResource(Res.string.suggestion_source_common_practice)
  TaskSourceKind.TASK_SOURCE_KIND_LOGS -> stringResource(Res.string.suggestion_source_logs)
  TaskSourceKind.TASK_SOURCE_KIND_UNSPECIFIED -> null
}
