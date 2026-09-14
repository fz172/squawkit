package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_confirm_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_confirm_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_dismiss
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_failed_duplicate
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_failed_parse
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_failed_unreadable
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_failed_unrecognized
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_keep_both
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_parsing
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_reading
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_import_storing

/** Progress, a probable-duplicate question, or a failure, inline in the list (PRD R35). */
@Composable
fun ImportRowCard(
  row: ImportRow,
  onKeepBoth: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val progress = row.progress
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(Spacing.large),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      when (progress) {
        is ImportProgress.Failed -> Icon(
          Icons.Filled.ErrorOutline, contentDescription = null,
          tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp),
        )
        is ImportProgress.NeedsConfirmation -> Icon(
          Icons.Filled.ErrorOutline, contentDescription = null,
          tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp),
        )
        else -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        when (progress) {
          ImportProgress.Reading -> Text(stringResource(Res.string.data_log_import_reading, row.file.name), style = MaterialTheme.typography.bodyMedium)
          is ImportProgress.Parsing -> Text(stringResource(Res.string.data_log_import_parsing, row.file.name), style = MaterialTheme.typography.bodyMedium)
          ImportProgress.Storing -> Text(stringResource(Res.string.data_log_import_storing, row.file.name), style = MaterialTheme.typography.bodyMedium)
          is ImportProgress.NeedsConfirmation -> {
            Text(stringResource(Res.string.data_log_import_confirm_title), style = MaterialTheme.typography.titleSmall)
            Text(
              stringResource(Res.string.data_log_import_confirm_body),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          is ImportProgress.Failed -> {
            Text(row.file.name, style = MaterialTheme.typography.titleSmall)
            Text(
              stringResource(progress.reason.message()),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          is ImportProgress.Done -> Unit
        }
      }
      when (progress) {
        is ImportProgress.NeedsConfirmation -> {
          TextButton(onClick = onDismiss) { Text(stringResource(Res.string.data_log_import_dismiss)) }
          TextButton(onClick = onKeepBoth) { Text(stringResource(Res.string.data_log_import_keep_both)) }
        }
        is ImportProgress.Failed -> TextButton(onClick = onDismiss) { Text(stringResource(Res.string.data_log_import_dismiss)) }
        else -> Unit
      }
    }
  }
}

private fun ImportFailure.message() = when (this) {
  ImportFailure.UNREADABLE -> Res.string.data_log_import_failed_unreadable
  ImportFailure.UNRECOGNIZED -> Res.string.data_log_import_failed_unrecognized
  ImportFailure.DUPLICATE -> Res.string.data_log_import_failed_duplicate
  ImportFailure.PARSE_ERROR -> Res.string.data_log_import_failed_parse
}
