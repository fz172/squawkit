package dev.fanfly.wingslog.core.file

import dev.fanfly.wingslog.core.file.GzipCodec.isAvailable
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/** `CompressionStream("gzip")` where the browser has it; [isAvailable] is false elsewhere. */
actual object GzipCodec {
  actual fun isAvailable(): Boolean =
    js("typeof CompressionStream !== 'undefined' && typeof DecompressionStream !== 'undefined'") as Boolean

  actual suspend fun compress(bytes: ByteArray): ByteArray =
    pipe(bytes, "CompressionStream")

  actual suspend fun decompress(bytes: ByteArray): ByteArray =
    try {
      pipe(bytes, "DecompressionStream")
    } catch (e: Throwable) {
      throw GzipException("not a gzip stream", e)
    }

  private suspend fun pipe(bytes: ByteArray, streamClass: String): ByteArray {
    if (!isAvailable()) throw GzipException("gzip streams are not available in this browser")
    val input = Uint8Array(
      bytes.unsafeCast<Int8Array>().buffer,
      bytes.unsafeCast<Int8Array>().byteOffset,
      bytes.size
    )
    val promise = js(
      "new Response(new Blob([input]).stream().pipeThrough(new (globalThis[streamClass])('gzip'))).arrayBuffer()",
    ) as Promise<ArrayBuffer>
    val buffer = promise.await()
    return Int8Array(buffer).unsafeCast<ByteArray>()
  }
}
