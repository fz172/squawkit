package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.cancel
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_duplicate
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_not_reproducible
import wingslog.feature.squawk.sharedassets.generated.resources.dismiss_reason_obsolete
import wingslog.feature.squawk.update.generated.resources.Res
import wingslog.feature.squawk.update.generated.resources.dismiss_confirm
import wingslog.feature.squawk.update.generated.resources.dismiss_squawk_title
import wingslog.feature.squawk.update.generated.resources.dismiss_squawk_warning

@Composable
fun DismissSquawkDialog(
  onConfirm: (SquawkDismissReason) -> Unit,
  onDismiss: () -> Unit,
) {
  var selected by remember { mutableStateOf<SquawkDismissReason?>(null) }

  val reasons = listOf(
    SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE to stringResource(SharedRes.string.dismiss_reason_obsolete),
    SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE to stringResource(SharedRes.string.dismiss_reason_not_reproducible),
    SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE to stringResource(SharedRes.string.dismiss_reason_duplicate),
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.dismiss_squawk_title)) },
    text = {
      Column {
        Text(
          text = stringResource(Res.string.dismiss_squawk_warning),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.medium))
        reasons.forEach { (reason, label) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = Spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = selected == reason,
              onClick = { selected = reason },
            )
            Text(
              text = label,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(start = Spacing.small),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { selected?.let(onConfirm) },
        enabled = selected != null,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(stringResource(Res.string.dismiss_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
