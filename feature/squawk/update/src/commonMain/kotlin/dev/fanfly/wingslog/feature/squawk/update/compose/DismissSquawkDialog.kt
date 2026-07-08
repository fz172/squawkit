package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.sharedassets.toLabel
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.squawk.update.generated.resources.Res
import wingslog.feature.squawk.update.generated.resources.dismiss_issue
import wingslog.feature.squawk.update.generated.resources.dismiss_squawk_title
import wingslog.feature.squawk.update.generated.resources.dismiss_squawk_warning
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
fun DismissSquawkDialog(
  onConfirm: (SquawkDismissReason) -> Unit,
  onDismiss: () -> Unit,
) {
  var selected by remember { mutableStateOf<SquawkDismissReason?>(null) }

  val reasons = listOf(
    SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE,
    SquawkDismissReason.SQUAWK_DISMISS_REASON_INTENDED_BEHAVIOR,
    SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
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
        reasons.forEach { reason ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(
                selected = selected == reason,
                onClick = { selected = reason },
                role = Role.RadioButton,
              )
              .padding(vertical = Spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = selected == reason,
              onClick = null,
            )
            Text(
              text = reason.toLabel(),
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
        Text(stringResource(Res.string.dismiss_issue))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
