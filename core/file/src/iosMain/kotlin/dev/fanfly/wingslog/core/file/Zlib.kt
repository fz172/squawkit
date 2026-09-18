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
import platform.zlib.MAX_WBITS
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

// zlib ships with Kotlin/Native's Apple platform libs and windowBits picks the framing: +16 writes
// the gzip header and CRC trailer, +32 auto-detects zlib or gzip on read, negative means neither.
internal const val GZIP_WINDOW_BITS = MAX_WBITS + 16
internal const val AUTO_DETECT_WINDOW_BITS = MAX_WBITS + 32
internal const val RAW_WINDOW_BITS = -MAX_WBITS

/** Runs one whole-buffer deflate or inflate; [error] names the exception the caller wants. */
@OptIn(ExperimentalForeignApi::class)
internal fun zlibRun(
  input: ByteArray,
  deflate: Boolean,
  windowBits: Int,
  error: (String) -> Throwable,
): ByteArray = memScoped {
  val memLevel = 8
  val stream = alloc<z_stream>()
  val init = if (deflate) {
    deflateInit2_(
      stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, windowBits, memLevel,
      Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().convert(),
    )
  } else {
    inflateInit2_(
      stream.ptr,
      windowBits,
      ZLIB_VERSION,
      sizeOf<z_stream>().convert()
    )
  }
  if (init != Z_OK) throw error("zlib init failed: $init")
  try {
    val chunks = ArrayList<ByteArray>()
    var total = 0
    val out = ByteArray(
      if (deflate) maxOf(
        input.size / 4,
        64 * 1024
      ) else maxOf(input.size * 4, 64 * 1024)
    )
    val source = if (input.isEmpty()) ByteArray(1) else input
    source.usePinned { pinnedIn ->
      stream.next_in = pinnedIn.addressOf(0)
        .reinterpret()
      stream.avail_in = input.size.convert()
      while (true) {
        val result = out.usePinned { pinnedOut ->
          stream.next_out = pinnedOut.addressOf(0)
            .reinterpret()
          stream.avail_out = out.size.convert()
          if (deflate) deflate(stream.ptr, Z_FINISH) else inflate(
            stream.ptr,
            Z_NO_FLUSH
          )
        }
        val produced = out.size - stream.avail_out.toInt()
        if (produced > 0) {
          chunks += out.copyOf(produced); total += produced
        }
        when (result) {
          Z_STREAM_END -> break
          Z_OK, Z_BUF_ERROR -> if (produced == 0 && stream.avail_in.toInt() == 0) {
            throw error("truncated stream")
          }

          else -> throw error(if (deflate) "deflate failed: $result" else "inflate failed: $result")
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
