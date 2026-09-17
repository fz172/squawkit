package dev.fanfly.wingslog.feature.attachment.viewing

import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.WebPickedFileRegistry
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/** Browser `File` handles, from an `<input type=file>` or a drop, as a `FileList`-like `dynamic`. */
internal fun fileHandles(files: dynamic): List<dynamic> {
  val length = files?.length.unsafeCast<Int?>() ?: 0
  return (0 until length).mapNotNull { index -> files.item(index) }
}

/**
 * Reads each file's bytes eagerly into [WebPickedFileRegistry]: a browser `File` is no stable
 * handle, so common code gets an opaque `web-picked:` URI instead.
 */
internal suspend fun readBrowserFiles(handles: List<dynamic>): DroppedFiles {
  var anyFailed = false
  val files = handles.mapNotNull { file ->
    val bytes = runCatching { readFileBytes(file) }
      .onFailure { anyFailed = true }
      .getOrNull()
      ?: return@mapNotNull null
    PickedFile(
      uri = WebPickedFileRegistry.put(bytes),
      name = file.name.unsafeCast<String>()
        .ifBlank { "file" },
      mimeType = file.type.unsafeCast<String>()
        .ifBlank { "application/octet-stream" },
      sizeBytes = file.size.unsafeCast<Number>()
        .toLong(),
    )
  }
  return DroppedFiles(files, anyFailed)
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
