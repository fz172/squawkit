@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

// Held for the duration of a pick — UIDocumentPickerViewController.delegate is weak, so nothing
// else retains these once rememberFilePicker's lambda returns.
private var activePicker: UIDocumentPickerViewController? = null
private var activeDelegate: NSObject? = null

/**
 * iOS file picker via [UIDocumentPickerViewController] in copy mode (`asCopy = true`), so the
 * picked URLs are app-readable copies and we don't have to juggle security-scoped resources. Each
 * pick is re-copied into the temp dir under its original name by [copyToTempPickedFile].
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

  private fun NSURL.toPickedFile(): PickedFile? {
    val sourcePath = path ?: return null
    return copyToTempPickedFile(sourcePath, name = lastPathComponent ?: "file")
  }

  private fun clearActive() {
    activePicker = null
    activeDelegate = null
  }
}
