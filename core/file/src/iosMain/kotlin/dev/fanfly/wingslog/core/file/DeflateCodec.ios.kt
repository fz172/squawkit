package dev.fanfly.wingslog.core.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Same zlib as [GzipCodec], with a negative windowBits so no header or trailer is written. */
actual object DeflateCodec {
  actual fun isAvailable(): Boolean = true

  actual suspend fun compress(bytes: ByteArray): ByteArray =
    withContext(Dispatchers.Default) {
      zlibRun(
        bytes,
        deflate = true,
        windowBits = RAW_WINDOW_BITS,
        error = { DeflateException(it) })
    }

  actual suspend fun decompress(bytes: ByteArray): ByteArray =
    withContext(Dispatchers.Default) {
      zlibRun(
        bytes,
        deflate = false,
        windowBits = RAW_WINDOW_BITS,
        error = { DeflateException(it) })
    }
}
