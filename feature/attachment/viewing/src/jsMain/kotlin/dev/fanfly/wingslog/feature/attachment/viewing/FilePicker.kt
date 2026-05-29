package dev.fanfly.wingslog.feature.attachment.viewing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.WebPickedFileRegistry
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLInputElement
import kotlin.js.Promise

@Composable
actual fun rememberFilePicker(
  onResult: (List<PickedFile>) -> Unit,
  onReadError: () -> Unit,
): () -> Unit {
  val scope = rememberCoroutineScope()
  return remember(onResult, onReadError, scope) {
    {
      val input = (document.createElement("input") as HTMLInputElement).apply {
        type = "file"
        multiple = true
      }
      input.onchange = {
        val files = input.asDynamic().files
        scope.launch {
          val pickedFiles = mutableListOf<PickedFile>()
          var readFailed = false
          val length = files?.length.unsafeCast<Int?>() ?: 0
          for (index in 0 until length) {
            val file = files.item(index) ?: continue
            val bytes = runCatching { readFileBytes(file) }
              .onFailure { readFailed = true }
              .getOrNull()
              ?: continue
            pickedFiles += PickedFile(
              uri = WebPickedFileRegistry.put(bytes),
              name = file.name.unsafeCast<String>()
                .ifBlank { "file" },
              mimeType = file.type.unsafeCast<String>()
                .ifBlank { "application/octet-stream" },
              sizeBytes = file.size.unsafeCast<Number>()
                .toLong(),
            )
          }
          if (readFailed) onReadError()
          if (pickedFiles.isNotEmpty()) onResult(pickedFiles)
        }
        null
      }
      input.click()
    }
  }
}

private suspend fun readFileBytes(file: dynamic): ByteArray {
  val buffer = file.arrayBuffer()
    .unsafeCast<Promise<dynamic>>()
    .await()
  return Uint8Array(buffer.unsafeCast<ArrayBuffer>()).toByteArray()
}

private fun Uint8Array.toByteArray(): ByteArray =
  ByteArray(length) { index ->
    asDynamic()[index].unsafeCast<Int>()
      .toByte()
  }
