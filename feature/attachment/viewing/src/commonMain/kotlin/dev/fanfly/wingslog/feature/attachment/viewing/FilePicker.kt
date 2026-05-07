package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

/**
 * Returns a lambda that, when invoked, opens the platform file picker.
 * Results are delivered via [onResult]. [onReadError] is called when the user
 * selected files but one or more could not be read (e.g., permission denied).
 *
 * Android: uses ActivityResultContracts.OpenMultipleDocuments.
 * iOS: stub in V1 — always delivers an empty list.
 */
@Composable
expect fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit = {},
): () -> Unit
