package dev.fanfly.wingslog.core.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** `nowrap = true` is what turns off the zlib header and Adler checksum, leaving raw deflate. */
actual object DeflateCodec {
  private const val BUFFER_SIZE = 16 * 1024

  actual fun isAvailable(): Boolean = true

  actual suspend fun compress(bytes: ByteArray): ByteArray =
    withContext(Dispatchers.Default) {
      val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
      try {
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream(bytes.size / 4 + BUFFER_SIZE)
        val buffer = ByteArray(BUFFER_SIZE)
        while (!deflater.finished()) {
          out.write(buffer, 0, deflater.deflate(buffer))
        }
        out.toByteArray()
      } finally {
        deflater.end()
      }
    }

  actual suspend fun decompress(bytes: ByteArray): ByteArray =
    withContext(Dispatchers.Default) {
      val inflater = Inflater(true)
      try {
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 4 + BUFFER_SIZE)
        val buffer = ByteArray(BUFFER_SIZE)
        // `Inflater` with nowrap wants one dummy byte past the end of the stream; it is documented,
        // and without it the final block never completes and the inflater just asks for more input.
        var fedTrailingByte = false
        while (!inflater.finished()) {
          val produced = inflater.inflate(buffer)
          if (produced > 0) {
            out.write(buffer, 0, produced)
            continue
          }
          if (inflater.needsInput() && !fedTrailingByte) {
            inflater.setInput(ByteArray(1))
            fedTrailingByte = true
            continue
          }
          throw DeflateException("truncated deflate stream")
        }
        out.toByteArray()
      } catch (e: DataFormatException) {
        throw DeflateException("not a deflate stream", e)
      } finally {
        inflater.end()
      }
    }
}
