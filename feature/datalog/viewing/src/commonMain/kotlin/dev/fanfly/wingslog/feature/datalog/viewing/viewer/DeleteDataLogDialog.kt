package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** "Delete this flight data?" — the viewer's one destructive action, behind a confirmation. */
@Composable
internal fun DeleteDataLogDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  val lexicon = LocalThingLexicon.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          Res.string.data_log_delete_title,
          LexiconFormatter.titleCase(lexicon.dataLogNoun)
        )
      )
    },
    text = { Text(stringResource(Res.string.data_log_delete_body)) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) { Text(stringResource(CoreRes.string.delete)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(
          stringResource(CoreRes.string.cancel)
        )
      }
    },
  )
}
