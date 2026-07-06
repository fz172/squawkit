package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

/**
 * Returns a lambda that, when invoked, opens the platform camera to capture a photo. The
 * captured photo is compressed below ~1 MB before [onResult] is called with a single-element
 * list. [onError] is called if the camera is unavailable or the capture/compression fails.
 *
 * Android: `ActivityResultContracts.TakePicture()` into a `FileProvider`-backed cache file.
 * iOS: `UIImagePickerController` with `sourceType = .camera`.
 * Not supported on web.
 */
@Composable
expect fun rememberCameraCapture(
  onResult: (List<PickedFile>) -> Unit,
  onError: () -> Unit = {},
): () -> Unit
