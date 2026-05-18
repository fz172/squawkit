package dev.fanfly.wingslog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import wingslog.composeapp.generated.resources.Res
import wingslog.composeapp.generated.resources.clearing_local_data
import wingslog.composeapp.generated.resources.database_error
import wingslog.composeapp.generated.resources.database_problem_message
import wingslog.composeapp.generated.resources.wipe_and_resync
import wingslog.composeapp.generated.resources.wiping

/**
 * Modal dialog shown when [DatabaseHealth.isCorrupted] is true. The user's only option is to
 * wipe local data and re-sync from Firestore; dismissing is not allowed since the app cannot
 * operate on a corrupted database.
 *
 * The [onWipe] callback should:
 * 1. Delete all local DB rows (entity, sync_cursor, blob_object).
 * 2. Sign the user out so the SyncEngine restarts and runs initial hydration on next sign-in.
 *
 * Blob files on disk are intentionally left behind; they become orphans that the reconciler
 * will clean up on the next successful sync session.
 */
@Composable
fun IntegrityRecoveryDialog(onWipe: suspend () -> Unit) {
  var wiping by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  AlertDialog(
    onDismissRequest = { /* non-dismissable */ },
    title = { Text(stringResource(Res.string.database_error)) },
    text = {
      Text(
        if (wiping) stringResource(Res.string.clearing_local_data)
        else stringResource(Res.string.database_problem_message)
      )
    },
    confirmButton = {
      Button(
        enabled = !wiping,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
        ),
        onClick = {
          wiping = true
          scope.launch {
            withContext(Dispatchers.Default) { onWipe() }
          }
        },
      ) {
        Text(
          if (wiping) stringResource(Res.string.wiping) else stringResource(
            Res.string.wipe_and_resync
          )
        )
      }
    },
  )
}
