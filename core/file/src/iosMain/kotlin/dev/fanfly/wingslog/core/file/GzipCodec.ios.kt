package dev.fanfly.wingslog.core.file

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.zlib.MAX_WBITS
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/**
 * zlib ships with Kotlin/Native's Apple platform libs and writes the gzip header and CRC trailer
 * itself (windowBits + 16), so no hand-rolled framing is needed.
 */
@OptIn(ExperimentalForeignApi::class)
actual object GzipCodec {
  private const val GZIP_WINDOW_BITS = MAX_WBITS + 16
  private const val AUTO_DETECT_WINDOW_BITS = MAX_WBITS + 32
  private const val MEM_LEVEL = 8

  actual fun isAvailable(): Boolean = true

  actual suspend fun compress(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    if (bytes.isEmpty()) return@withContext run(ByteArray(0), deflate = true)
    run(bytes, deflate = true)
  }

  actual suspend fun decompress(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    run(bytes, deflate = false)
  }

  private fun run(input: ByteArray, deflate: Boolean): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    val init = if (deflate) {
      deflateInit2_(
        stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, GZIP_WINDOW_BITS, MEM_LEVEL,
        Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().convert(),
      )
    } else {
      inflateInit2_(stream.ptr, AUTO_DETECT_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().convert())
    }
    if (init != Z_OK) throw GzipException("zlib init failed: $init")
    try {
      val chunks = ArrayList<ByteArray>()
      var total = 0
      val out = ByteArray(if (deflate) maxOf(input.size / 4, 64 * 1024) else maxOf(input.size * 4, 64 * 1024))
      val source = if (input.isEmpty()) ByteArray(1) else input
      source.usePinned { pinnedIn ->
        stream.next_in = pinnedIn.addressOf(0).reinterpret()
        stream.avail_in = input.size.convert()
        while (true) {
          val result = out.usePinned { pinnedOut ->
            stream.next_out = pinnedOut.addressOf(0).reinterpret()
            stream.avail_out = out.size.convert()
            if (deflate) deflate(stream.ptr, Z_FINISH) else inflate(stream.ptr, Z_NO_FLUSH)
          }
          val produced = out.size - stream.avail_out.toInt()
          if (produced > 0) { chunks += out.copyOf(produced); total += produced }
          when (result) {
            Z_STREAM_END -> break
            Z_OK, Z_BUF_ERROR -> if (produced == 0 && stream.avail_in.toInt() == 0) {
              throw GzipException("truncated gzip stream")
            }
            else -> throw GzipException(if (deflate) "deflate failed: $result" else "not a gzip stream: $result")
          }
        }
      }
      val joined = ByteArray(total)
      var offset = 0
      chunks.forEach { it.copyInto(joined, offset); offset += it.size }
      joined
    } finally {
      if (deflate) deflateEnd(stream.ptr) else inflateEnd(stream.ptr)
    }
  }
}
