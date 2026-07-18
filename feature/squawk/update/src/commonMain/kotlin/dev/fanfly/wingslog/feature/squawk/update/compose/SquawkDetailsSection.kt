package dev.fanfly.wingslog.feature.squawk.update.compose

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
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.sharedassets.toLabel
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.logs.sharedassets.generated.resources.maintenance_history
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.dismissed_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_description_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_not_yet_addressed
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes

@Composable
fun SquawkDetailsSection(
  description: String,
  onDescriptionChange: (String) -> Unit,
  isEdit: Boolean,
  addressedByLogId: String,
  availableLogs: List<MaintenanceLog>,
  onAddLog: () -> Unit,
  onClearLog: () -> Unit,
  readOnly: Boolean,
  dismissReason: SquawkDismissReason,
  dismissedAtFormatted: String,
  attachmentSection: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isDismissed =
    dismissReason != SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    FormTextField(
      value = description,
      onValueChange = onDescriptionChange,
      label = stringResource(Res.string.squawk_description_label),
      singleLine = false,
      keyboardOptions = FormKeyboard.Sentences,
      minLines = 4,
      modifier = Modifier.fillMaxWidth(),
      editable = !readOnly,
    )

    // Maintenance history (edit mode only)
    if (isEdit) {
      val associatedLog =
        availableLogs.firstOrNull { it.id == addressedByLogId }

      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          FormSectionLabel(stringResource(LogsRes.string.maintenance_history))
          if (addressedByLogId.isEmpty() && !isDismissed) {
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
        }

        when {
          isDismissed -> {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
              Text(
                text = stringResource(
                  Res.string.dismissed_label,
                  dismissReason.toLabel()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (dismissedAtFormatted.isNotEmpty()) {
                Text(
                  text = dismissedAtFormatted,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          addressedByLogId.isEmpty() -> Text(
            text = stringResource(Res.string.squawk_not_yet_addressed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          else -> {
            val displayText =
              associatedLog?.work_description?.takeIf { it.isNotBlank() }
                ?: addressedByLogId
            val logDate = associatedLog?.timestamp
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
                IconButton(onClick = onClearLog) {
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

    // Attachments
    attachmentSection()
  }
}
