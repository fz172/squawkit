package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

/**
 * Returns a lambda that, when invoked, opens the platform photo library picker.
 * Results are delivered via [onResult]. [onReadError] is called when the user
 * selected photos but one or more could not be read.
 *
 * Android: `ActivityResultContracts.PickMultipleVisualMedia()` (images and videos).
 * iOS: `PHPickerViewController` filtered to images and videos.
 * Web: file input filtered to images and videos.
 */
@Composable
expect fun rememberPhotoPicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit = {},
): () -> Unit
