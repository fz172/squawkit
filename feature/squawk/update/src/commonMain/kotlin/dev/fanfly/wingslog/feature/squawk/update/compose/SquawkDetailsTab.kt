package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_addressing_log
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_description_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_not_yet_addressed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_view_log

@Composable
fun SquawkDetailsTab(
  description: String,
  onDescriptionChange: (String) -> Unit,
  isEdit: Boolean,
  addressedByLogId: String,
  onViewLog: (() -> Unit)?,
  readOnly: Boolean,
  attachmentSection: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    // Description
    OutlinedTextField(
      value = description,
      onValueChange = onDescriptionChange,
      label = { Text(stringResource(Res.string.squawk_description_label)) },
      minLines = 4,
      modifier = Modifier.fillMaxWidth(),
      readOnly = readOnly,
    )

    // Addressing log (edit mode only)
    if (isEdit) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
          text = stringResource(Res.string.squawk_addressing_log),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (addressedByLogId.isNotEmpty() && onViewLog != null) {
          OutlinedButton(
            onClick = onViewLog,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(stringResource(Res.string.squawk_view_log))
          }
        } else {
          Text(
            text = stringResource(Res.string.squawk_not_yet_addressed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Attachments
    attachmentSection()
  }
}
