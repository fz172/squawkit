package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.attachments.datamanager.PickedFile

/** iOS file picker — not implemented in V1. Always delivers an empty result. */
@Composable
actual fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit = { }
