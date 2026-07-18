package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

/**
 * Returns a lambda that, when invoked, opens the platform camera to capture a photo, then calls
 * [onResult] with a single-element list. [onError] is called if the camera is unavailable or the
 * capture fails.
 *
 * The captured photo is handed back at full size; downscaling/compression happens downstream in
 * `AttachmentManager.addPickedFile`, the single point shared with the file-picker flow.
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
