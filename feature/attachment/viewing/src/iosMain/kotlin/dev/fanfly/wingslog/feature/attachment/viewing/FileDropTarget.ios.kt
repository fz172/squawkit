package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSItemProvider
import platform.UIKit.UIDragItem
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.conformsToType
import kotlin.coroutines.resume

@Composable
internal actual fun rememberDroppedFileReader(): DroppedFileReader = IosDroppedFileReader

// The platform ends the drag session itself when a drag leaves or is cancelled.
@Composable
internal actual fun DragLeftWindowEffect(onLeft: () -> Unit) = Unit

@OptIn(ExperimentalComposeUiApi::class)
private object IosDroppedFileReader : DroppedFileReader {
  override fun carriesFiles(event: DragAndDropEvent): Boolean =
    event.items.any { it.isExternalData() }

  override fun claim(event: DragAndDropEvent): (suspend () -> DroppedFiles)? {
    val providers = event.items.filter { it.isExternalData() }
      .map { it.itemProvider }
    if (providers.isEmpty()) return null
    return {
      val files = providers.map { it.loadPickedFile() }
      DroppedFiles(files.filterNotNull(), anyFailed = null in files)
    }
  }
}

// An in-app drag carries a local object; a file from Files or Photos carries data.
private fun UIDragItem.isExternalData(): Boolean =
  localObject == null && itemProvider.dataTypeIdentifier() != null

private fun NSItemProvider.dataTypeIdentifier(): String? =
  registeredTypeIdentifiers.filterIsInstance<String>()
    .firstOrNull { UTType.typeWithIdentifier(it)?.conformsToType(UTTypeData) == true }

private suspend fun NSItemProvider.loadPickedFile(): PickedFile? {
  val typeIdentifier = dataTypeIdentifier() ?: return null
  return suspendCancellableCoroutine { continuation ->
    loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, _ ->
      // The system deletes the file when this handler returns, so the copy happens here.
      val picked = url?.path?.let { path ->
        copyToTempPickedFile(path, name = fileName(typeIdentifier, url.lastPathComponent))
      }
      continuation.resume(picked)
    }
  }
}

// suggestedName is the name the source app shows, often without an extension; the extension keeps
// the MIME lookup and the attachment icon right.
private fun NSItemProvider.fileName(typeIdentifier: String, fallback: String?): String {
  val suggested = suggestedName?.takeIf { it.isNotBlank() } ?: return fallback ?: "file"
  if ('.' in suggested) return suggested
  val extension = UTType.typeWithIdentifier(typeIdentifier)?.preferredFilenameExtension
  return if (extension == null) suggested else "$suggested.$extension"
}
