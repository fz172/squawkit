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
    title = { Text("Database Error") },
    text = {
      Text(
        if (wiping) "Clearing local data…"
        else "A problem was detected with the local database. Tap below to wipe your device's " +
          "copy and re-sync from the cloud. Your cloud data is safe and will be restored."
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
        Text(if (wiping) "Wiping…" else "Wipe and Re-sync")
      }
    },
  )
}
