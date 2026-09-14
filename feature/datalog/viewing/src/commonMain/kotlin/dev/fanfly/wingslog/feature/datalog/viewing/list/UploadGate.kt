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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.common.compose.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_bullet_attach
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_bullet_columns
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_bullet_map
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_bullet_panes
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_link_cta
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_not_now
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_open_settings
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_sheet_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_sheet_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_gate_what_title

/**
 * Wide-layout guest state (PRD R40, mock 3a): one *Link to an account* action that routes like the
 * phone does, and the "what gets charted" list. The login providers live in `feature/login`, which
 * depends on other features, so this card does not embed them (design §8.4).
 */
@Composable
fun UploadGateCard(onLinkAccount: () -> Unit, modifier: Modifier = Modifier) {
  val noun = LocalThingLexicon.current.dataLogNoun.singular
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(Spacing.extraLarge),
      horizontalArrangement = Arrangement.spacedBy(Spacing.huge),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium)
      ) {
        Icon(
          Icons.Filled.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(Spacing.extraLarge)
        )
        Text(
          stringResource(Res.string.data_log_gate_title, noun),
          style = MaterialTheme.typography.titleLarge
        )
        Text(
          stringResource(Res.string.data_log_gate_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLinkAccount) {
          Icon(
            Icons.Filled.Link,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
          )
          Text(
            stringResource(Res.string.data_log_gate_link_cta),
            modifier = Modifier.padding(start = Spacing.small)
          )
        }
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
        Text(
          stringResource(Res.string.data_log_gate_what_title).uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
          Res.string.data_log_gate_bullet_columns,
          Res.string.data_log_gate_bullet_panes,
          Res.string.data_log_gate_bullet_map,
          Res.string.data_log_gate_bullet_attach,
        ).forEach { line ->
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
          ) {
            Icon(
              Icons.Filled.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Text(
              stringResource(line),
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }
    }
  }
}

/** Phone guest prompt (PRD R40, mock 3b): *Open Settings* lands on the link-account sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkAccountPromptSheet(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
  val noun = LocalThingLexicon.current.dataLogNoun.singular
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(horizontal = Spacing.extraLarge, vertical = Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        Icons.Filled.Link,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(Spacing.extraLarge)
      )
      Text(
        stringResource(Res.string.data_log_gate_sheet_title),
        style = MaterialTheme.typography.titleLarge
      )
      Text(
        stringResource(Res.string.data_log_gate_sheet_body, noun),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
        Icon(
          Icons.Filled.Settings,
          contentDescription = null,
          modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Text(
          stringResource(Res.string.data_log_gate_open_settings),
          modifier = Modifier.padding(start = Spacing.small)
        )
      }
      TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.data_log_gate_not_now))
      }
    }
  }
}
