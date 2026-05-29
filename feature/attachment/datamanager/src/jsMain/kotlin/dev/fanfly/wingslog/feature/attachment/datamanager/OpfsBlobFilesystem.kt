package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Browser [BlobFilesystem] backed by the Origin Private File System. Bytes live under
 * `OPFS/hopply/blobs/{id}.bin`, outside the SQLite OPFS file but under the same origin.
 */
internal class OpfsBlobFilesystem : BlobFilesystem {
  override suspend fun write(relativePath: String, bytes: ByteArray) {
    val fileHandle = fileHandle(relativePath, create = true)
    val writable = fileHandle.createWritable()
      .unsafeCast<Promise<dynamic>>()
      .await()
    writable.write(bytes.toUint8Array())
      .unsafeCast<Promise<Unit>>()
      .await()
    writable.close()
      .unsafeCast<Promise<Unit>>()
      .await()
  }

  override suspend fun read(relativePath: String): ByteArray {
    val fileHandle = fileHandle(relativePath, create = false)
    val file = fileHandle.getFile()
      .unsafeCast<Promise<dynamic>>()
      .await()
    val buffer = file.arrayBuffer()
      .unsafeCast<Promise<dynamic>>()
      .await()
    return Uint8Array(buffer.unsafeCast<ArrayBuffer>()).toByteArray()
  }

  override suspend fun delete(relativePath: String) {
    val (directory, name) = parentDirectory(relativePath, create = false)
      ?: return
    runCatching {
      directory.removeEntry(name)
        .unsafeCast<Promise<Unit>>()
        .await()
    }
  }

  override suspend fun exists(relativePath: String): Boolean =
    runCatching {
      fileHandle(relativePath, create = false)
      true
    }.getOrDefault(false)

  override fun uriFor(relativePath: String): String =
    "opfs://hopply/$relativePath"

  private suspend fun fileHandle(
    relativePath: String,
    create: Boolean
  ): dynamic {
    val (directory, name) = parentDirectory(relativePath, create)
      ?: throw IllegalStateException("OPFS path does not exist: $relativePath")
    return directory.getFileHandle(name, options(create))
      .unsafeCast<Promise<dynamic>>()
      .await()
  }

  private suspend fun parentDirectory(
    relativePath: String,
    create: Boolean,
  ): Pair<dynamic, String>? {
    val parts = relativePath.split('/')
      .filter { it.isNotBlank() }
    require(parts.isNotEmpty()) { "relativePath must include a file name" }

    var directory = opfsRoot()
      .getDirectoryHandle(APP_DIRECTORY, options(create))
      .unsafeCast<Promise<dynamic>>()
      .await()

    for (part in parts.dropLast(1)) {
      directory = try {
        directory.getDirectoryHandle(part, options(create))
          .unsafeCast<Promise<dynamic>>()
          .await()
      } catch (e: Throwable) {
        if (create) throw e else return null
      }
    }

    return directory to parts.last()
  }

  private suspend fun opfsRoot(): dynamic {
    val storage = js("globalThis.navigator && globalThis.navigator.storage")
      ?: throw UnsupportedOperationException("OPFS is not available in this browser")
    return storage.getDirectory()
      .unsafeCast<Promise<dynamic>>()
      .await()
  }

  private fun options(create: Boolean): dynamic {
    val options = js("({})")
    options.create = create
    return options
  }

  private fun ByteArray.toUint8Array(): Uint8Array =
    Uint8Array(toTypedArray())

  private fun Uint8Array.toByteArray(): ByteArray =
    ByteArray(length) { index ->
      asDynamic()[index].unsafeCast<Int>()
        .toByte()
    }

  private companion object {
    private const val APP_DIRECTORY = "hopply"
  }
}
