package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject

// Held for the duration of a pick — PHPickerViewController.delegate is weak, so nothing
// else retains these once rememberPhotoPicker's lambda returns.
private var activePicker: PHPickerViewController? = null
private var activeDelegate: NSObject? = null

@Composable
actual fun rememberPhotoPicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit {
  val scope = rememberCoroutineScope()
  return {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (root == null) {
      onReadError()
    } else {
      val delegate = PhotoPickerDelegate(scope, onResult, onReadError)
      val config = PHPickerConfiguration().apply {
        filter = PHPickerFilter.anyFilterMatchingSubfilters(
          listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter)
        )
        selectionLimit = 0
      }
      val picker = PHPickerViewController(configuration = config).apply {
        setDelegate(delegate)
      }
      activePicker = picker
      activeDelegate = delegate
      root.presentViewController(picker, animated = true, completion = null)
    }
  }
}

private class PhotoPickerDelegate(
  private val scope: CoroutineScope,
  private val onResult: (List<PickedFile>) -> Unit,
  private val onReadError: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

  override fun picker(
    picker: PHPickerViewController,
    didFinishPicking: List<*>,
  ) {
    picker.dismissViewControllerAnimated(true, completion = null)
    clearActive()
    val results = didFinishPicking.filterIsInstance<PHPickerResult>()
    if (results.isEmpty()) return
    scope.launch {
      val files = results.map { it.itemProvider.loadPickedFile() }
      if (null in files) onReadError()
      onResult(files.filterNotNull())
    }
  }

  private fun clearActive() {
    activePicker = null
    activeDelegate = null
  }
}
