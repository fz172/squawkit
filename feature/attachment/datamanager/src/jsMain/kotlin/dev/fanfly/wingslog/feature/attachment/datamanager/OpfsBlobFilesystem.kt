package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import kotlin.js.Promise
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array

/**
 * Browser [BlobFilesystem] backed by the Origin Private File System. Bytes live under
 * `OPFS/squawkit/blobs/{id}.bin`, outside the SQLite OPFS file but under the same origin.
 *
 * **Important Kotlin/JS detail:** every navigation step (`getDirectory`, `getDirectoryHandle`,
 * `getFileHandle`, `createWritable`, `getFile`, `arrayBuffer`, `write`, `close`) is materialized
 * into its own `val` before being awaited. Chaining `awaitedPromise.method().await()` on a `dynamic`
 * receiver makes the JS compiler emit only one state-machine suspension and drop the earlier ones,
 * so `getDirectoryHandle` ends up being called on an unresolved Continuation token instead of the
 * directory handle. Keep one suspend point per statement.
 */
internal class OpfsBlobFilesystem : BlobFilesystem {
  override suspend fun write(relativePath: String, bytes: ByteArray) {
    val fileHandle = fileHandle(relativePath, create = true)
    val writablePromise = fileHandle.createWritable()
      .unsafeCast<Promise<dynamic>>()
    val writable = writablePromise.await()
    val writePromise = writable.write(bytes.toUint8Array())
      .unsafeCast<Promise<Unit>>()
    writePromise.await()
    val closePromise = writable.close()
      .unsafeCast<Promise<Unit>>()
    closePromise.await()
  }

  override suspend fun read(relativePath: String): ByteArray {
    val fileHandle = fileHandle(relativePath, create = false)
    val filePromise = fileHandle.getFile()
      .unsafeCast<Promise<dynamic>>()
    val file = filePromise.await()
    val bufferPromise = file.arrayBuffer()
      .unsafeCast<Promise<dynamic>>()
    val buffer = bufferPromise.await()
    return Uint8Array(buffer.unsafeCast<ArrayBuffer>()).toByteArray()
  }

  override suspend fun delete(relativePath: String) {
    val parent = parentDirectory(relativePath, create = false) ?: return
    val (directory, name) = parent
    runCatching {
      val removePromise = directory.removeEntry(name)
        .unsafeCast<Promise<Unit>>()
      removePromise.await()
    }
  }

  override suspend fun exists(relativePath: String): Boolean =
    runCatching {
      fileHandle(relativePath, create = false)
      true
    }.getOrDefault(false)

  override fun uriFor(relativePath: String): String =
    "opfs://squawkit/$relativePath"

  private suspend fun fileHandle(
    relativePath: String,
    create: Boolean,
  ): dynamic {
    val parent = parentDirectory(relativePath, create)
      ?: throw IllegalStateException("OPFS path does not exist: $relativePath")
    val (directory, name) = parent
    val handlePromise = directory.getFileHandle(name, options(create))
      .unsafeCast<Promise<dynamic>>()
    return handlePromise.await()
  }

  private suspend fun parentDirectory(
    relativePath: String,
    create: Boolean,
  ): Pair<dynamic, String>? {
    val parts = relativePath.split('/')
      .filter { it.isNotBlank() }
    require(parts.isNotEmpty()) { "relativePath must include a file name" }

    val root = opfsRoot()
    val appDirPromise = root.getDirectoryHandle(APP_DIRECTORY, options(create))
      .unsafeCast<Promise<dynamic>>()
    var directory: dynamic = appDirPromise.await()

    for (part in parts.dropLast(1)) {
      val nextPromise = try {
        directory.getDirectoryHandle(part, options(create))
          .unsafeCast<Promise<dynamic>>()
      } catch (e: Throwable) {
        if (create) throw e else return null
      }
      directory = try {
        nextPromise.await()
      } catch (e: Throwable) {
        if (create) throw e else return null
      }
    }

    // Pair(...) instead of `to` infix: `directory` is `dynamic`, so `directory to x` compiles
    // to `directory.to(x)` as a JS method call instead of dispatching through the extension.
    return Pair(directory, parts.last())
  }

  private suspend fun opfsRoot(): dynamic {
    val storage = js("globalThis.navigator && globalThis.navigator.storage")
      ?: throw UnsupportedOperationException("OPFS is not available in this browser")
    val rootPromise = storage.getDirectory()
      .unsafeCast<Promise<dynamic>>()
    return rootPromise.await()
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
    private const val APP_DIRECTORY = "squawkit"
  }
}
