package dev.fanfly.wingslog.feature.attachment.viewing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

@Composable
actual fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris: List<Uri> ->
    val picked = context.toPickedFiles(uris)
    if (picked.anyFailed) onReadError()
    onResult(picked.files)
  }
  return { launcher.launch(arrayOf("*/*")) }
}
