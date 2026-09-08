package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.delete_squawk_confirmation
import wingslog.feature.squawk.sharedassets.generated.resources.delete_squawk_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * Confirms deleting a squawk. The copy says what is kept (the addressing log) and points at
 * Dismiss for a defect that was real: deletion is for entries made in error (PRD R12).
 */
@Composable
fun DeleteSquawkConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val lexicon = LocalThingLexicon.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.delete_squawk_title, lexicon.squawkNoun.singular)) },
    text = {
      Text(
        stringResource(
          Res.string.delete_squawk_confirmation,
          lexicon.squawkNoun.singular,
          lexicon.logNoun.singular,
        )
      )
    },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(stringResource(CoreRes.string.delete))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}
