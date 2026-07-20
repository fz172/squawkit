@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

// Held for the duration of a pick — UIDocumentPickerViewController.delegate is weak, so nothing
// else retains these once rememberFilePicker's lambda returns.
private var activePicker: UIDocumentPickerViewController? = null
private var activeDelegate: NSObject? = null

/**
 * iOS file picker via [UIDocumentPickerViewController] in copy mode (`asCopy = true`), so the
 * picked URLs are app-readable copies and we don't have to juggle security-scoped resources. Each
 * pick is re-copied into the temp dir under its original name and handed back as a [PickedFile]
 * whose `uri` is an absolute path — the shape [FileByteReaderImpl] and the camera flow expect.
 */
@Composable
actual fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit = {
  val root = UIApplication.sharedApplication.keyWindow?.rootViewController
  if (root == null) {
    onReadError()
  } else {
    val delegate = DocumentPickerDelegate(onResult, onReadError)
    val picker = UIDocumentPickerViewController(
      forOpeningContentTypes = listOf(UTTypeItem),
      asCopy = true,
    ).apply {
      allowsMultipleSelection = true
      setDelegate(delegate)
    }
    activePicker = picker
    activeDelegate = delegate
    root.presentViewController(picker, animated = true, completion = null)
  }
}

private class DocumentPickerDelegate(
  private val onResult: (List<PickedFile>) -> Unit,
  private val onReadError: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

  override fun documentPicker(
    controller: UIDocumentPickerViewController,
    didPickDocumentsAtURLs: List<*>,
  ) {
    controller.dismissViewControllerAnimated(true, completion = null)
    clearActive()
    val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
    var anyFailed = false
    val files = urls.mapNotNull { url ->
      val picked = runCatching { url.toPickedFile() }.getOrNull()
      if (picked == null) anyFailed = true
      picked
    }
    if (anyFailed) onReadError()
    onResult(files)
  }

  override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
    controller.dismissViewControllerAnimated(true, completion = null)
    clearActive()
  }

  /** Copy the picked file into our temp dir under its display name; null if it can't be read. */
  private fun NSURL.toPickedFile(): PickedFile? {
    val sourcePath = path ?: return null
    val name = lastPathComponent ?: "file"
    val destPath = "${NSTemporaryDirectory()}picked_${NSUUID().UUIDString()}_$name"
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(destPath, null)
    if (!fm.copyItemAtPath(sourcePath, toPath = destPath, error = null)) return null
    val size = (fm.attributesOfItemAtPath(destPath, null)?.get(NSFileSize) as? NSNumber)
      ?.longLongValue ?: 0L
    return PickedFile(
      uri = destPath,
      name = name,
      mimeType = mimeTypeForName(name),
      sizeBytes = size,
    )
  }

  private fun clearActive() {
    activePicker = null
    activeDelegate = null
  }
}

/** Best-effort MIME from a filename's extension via the system UTType database. */
private fun mimeTypeForName(name: String): String {
  val ext = name.substringAfterLast('.', "")
  if (ext.isEmpty()) return "application/octet-stream"
  return UTType.typeWithFilenameExtension(ext)?.preferredMIMEType ?: "application/octet-stream"
}
