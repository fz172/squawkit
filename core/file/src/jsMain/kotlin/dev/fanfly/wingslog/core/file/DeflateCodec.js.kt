package dev.fanfly.wingslog.core.file

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * `CompressionStream("deflate-raw")`. The class itself is older than the `deflate-raw` format, so
 * availability is decided by constructing one rather than by looking the class up.
 */
actual object DeflateCodec {
  private val supported: Boolean by lazy {
    js(
      "(function(){try{new CompressionStream('deflate-raw');new DecompressionStream('deflate-raw');" +
        "return true}catch(e){return false}})()",
    ) as Boolean
  }

  actual fun isAvailable(): Boolean = supported

  actual suspend fun compress(bytes: ByteArray): ByteArray =
    pipe(bytes, "CompressionStream")

  actual suspend fun decompress(bytes: ByteArray): ByteArray =
    try {
      pipe(bytes, "DecompressionStream")
    } catch (e: Throwable) {
      throw DeflateException("not a deflate stream", e)
    }

  private suspend fun pipe(bytes: ByteArray, streamClass: String): ByteArray {
    if (!isAvailable()) throw DeflateException("deflate-raw streams are not available in this browser")
    val input = Uint8Array(
      bytes.unsafeCast<Int8Array>().buffer,
      bytes.unsafeCast<Int8Array>().byteOffset,
      bytes.size
    )
    val promise = js(
      "new Response(new Blob([input]).stream().pipeThrough(new (globalThis[streamClass])('deflate-raw'))).arrayBuffer()",
    ) as Promise<ArrayBuffer>
    val buffer = promise.await()
    return Int8Array(buffer).unsafeCast<ByteArray>()
  }
}
