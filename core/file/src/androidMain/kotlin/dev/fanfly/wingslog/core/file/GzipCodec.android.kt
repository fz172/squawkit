package dev.fanfly.wingslog.core.file

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object GzipCodec {
  actual fun isAvailable(): Boolean = true

  actual suspend fun compress(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    val out = ByteArrayOutputStream(bytes.size / 4 + 64)
    GZIPOutputStream(out).use { it.write(bytes) }
    out.toByteArray()
  }

  actual suspend fun decompress(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    try {
      GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    } catch (e: java.io.IOException) {
      throw GzipException("not a gzip stream", e)
    }
  }
}
