package dev.fanfly.wingslog.core.attachments.viewing

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.attachments.datamanager.PickedFile

/**
 * Returns a lambda that, when invoked, opens the platform file picker.
 * Results are delivered via [onResult].
 *
 * Android: uses ActivityResultContracts.OpenMultipleDocuments.
 * iOS: stub in V1 — always delivers an empty list.
 */
@Composable
expect fun rememberFilePicker(onResult: (List<PickedFile>) -> Unit): () -> Unit
