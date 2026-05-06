package dev.fanfly.wingslog.feature.attachment.viewing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.fanfly.wingslog.core.attachments.datamanager.PickedFile

@Composable
actual fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris: List<Uri> ->
    val files = uris.mapNotNull { uri ->
      try {
        val mimeType = context.contentResolver.getType(uri) ?: "*/*"
        val name = uri.lastPathSegment?.substringAfterLast("/") ?: "file"
        val sizeBytes = context.contentResolver.openFileDescriptor(uri, "r")
          ?.use { it.statSize } ?: 0L
        PickedFile(uri = uri.toString(), name = name, mimeType = mimeType, sizeBytes = sizeBytes)
      } catch (e: Exception) {
        null
      }
    }
    if (uris.isNotEmpty() && files.size < uris.size) onReadError()
    onResult(files)
  }
  return { launcher.launch(arrayOf("*/*")) }
}
