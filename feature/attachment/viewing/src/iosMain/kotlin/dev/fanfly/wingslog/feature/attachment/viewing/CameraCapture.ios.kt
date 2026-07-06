package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

private const val MAX_PHOTO_BYTES = 1_000_000L
private const val MAX_DIMENSION = 2048.0

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

  @OptIn(ExperimentalForeignApi::class)
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
      val data = compressToJpeg(image)
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

@OptIn(ExperimentalForeignApi::class)
private fun compressToJpeg(original: UIImage) = run {
  var image = original.resizedIfNeeded(MAX_DIMENSION)
  var quality = 0.9
  var data = UIImageJPEGRepresentation(image, quality)
  while ((data == null || data.length.toLong() > MAX_PHOTO_BYTES) && quality > 0.15) {
    quality -= 0.15
    data = UIImageJPEGRepresentation(image, quality)
  }
  if (data == null || data.length.toLong() > MAX_PHOTO_BYTES) {
    image = image.resizedIfNeeded(MAX_DIMENSION * 0.6)
    data = UIImageJPEGRepresentation(image, 0.6)
  }
  requireNotNull(data) { "could not JPEG-encode captured photo" }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.resizedIfNeeded(maxDimension: Double): UIImage {
  val (width, height) = size.useContents { width to height }
  val longestSide = maxOf(width, height)
  if (longestSide <= maxDimension) return this
  val scale = maxDimension / longestSide
  val newSize = CGSizeMake(width * scale, height * scale)
  UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
  try {
    drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    return UIGraphicsGetImageFromCurrentImageContext() ?: this
  } finally {
    UIGraphicsEndImageContext()
  }
}
