package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

// Kept alive for the duration of a capture — UIImagePickerController.delegate is a weak
// reference, so nothing else holds this once rememberCameraCapture's lambda returns.
private var activePicker: UIImagePickerController? = null
private var activeDelegate: NSObject? = null

@Composable
actual fun rememberCameraCapture(
  onResult: (List<PickedFile>) -> Unit,
  onError: () -> Unit,
): () -> Unit = {
  if (!UIImagePickerController.isSourceTypeAvailable(
      UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    )
  ) {
    onError()
  } else {
    val rootViewController =
      UIApplication.sharedApplication.keyWindow?.rootViewController
    if (rootViewController == null) {
      onError()
    } else {
      val delegate = CameraPickerDelegate(
        onResult = onResult,
        onError = onError,
      )
      val picker = UIImagePickerController().apply {
        sourceType =
          UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        setDelegate(delegate)
      }
      activePicker = picker
      activeDelegate = delegate
      rootViewController.presentViewController(
        picker,
        animated = true,
        completion = null
      )
    }
  }
}

private class CameraPickerDelegate(
  private val onResult: (List<PickedFile>) -> Unit,
  private val onError: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol,
  UINavigationControllerDelegateProtocol {

  override fun imagePickerController(
    picker: UIImagePickerController,
    didFinishPickingMediaWithInfo: Map<Any?, *>,
  ) {
    picker.dismissViewControllerAnimated(true, completion = null)
    val image =
      didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
    clearActive()
    if (image == null) {
      onError()
      return
    }
    try {
      // Serialize the captured image to a JPEG file and hand it back raw. Downscaling/compression
      // happens downstream in AttachmentManager.addPickedFile — the single point shared with the
      // file-picker flow.
      val data = UIImageJPEGRepresentation(image, 1.0)
      if (data == null) {
        onError()
        return
      }
      val path = NSTemporaryDirectory() + "capture_${NSUUID().UUIDString()}.jpg"
      if (!data.writeToFile(path, atomically = true)) {
        onError()
        return
      }
      onResult(
        listOf(
          PickedFile(
            uri = path,
            name = "IMG_${
              NSUUID().UUIDString()
                .take(8)
            }.jpg",
            mimeType = "image/jpeg",
            sizeBytes = data.length.toLong(),
          )
        )
      )
    } catch (e: Exception) {
      onError()
    }
  }

  override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
    picker.dismissViewControllerAnimated(true, completion = null)
    clearActive()
  }

  private fun clearActive() {
    activePicker = null
    activeDelegate = null
  }
}
