package dev.fanfly.wingslog.feature.technician.manage.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_action
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_dismiss
import wingslog.feature.technician.sharedassets.generated.resources.duplicates_prompt_title
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes

/**
 * Dismissible nudge that look-alike rows are worth reconciling (design §7.4). "Not duplicates" is a
 * real answer — it records that the user has looked, so the prompt does not nag again.
 */
@Composable
internal fun DuplicatePrompt(
  onReview: () -> Unit,
  onDismiss: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
        text = stringResource(TechnicianRes.string.duplicates_prompt_title),
        style = MaterialTheme.typography.bodyMedium,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TextButton(onClick = onReview) {
          Text(stringResource(TechnicianRes.string.duplicates_prompt_action))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(TechnicianRes.string.duplicates_prompt_dismiss))
        }
      }
    }
  }
}
