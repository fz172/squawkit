package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

actual val isCameraCaptureSupported: Boolean = false

/** Not supported on web — gated off by [isCameraCaptureSupported]. */
@Composable
actual fun rememberCameraCapture(
  onResult: (List<PickedFile>) -> Unit,
  onError: () -> Unit,
): () -> Unit = {}
