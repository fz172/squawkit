package dev.fanfly.wingslog.core.file

/**
 * Raw deflate — the bare DEFLATE stream with no zlib or gzip framing, which is what a ZIP entry
 * stores. Sibling of [GzipCodec]: same platform backends, same suspending shape, different framing.
 * Callers that can fall back (a ZIP entry can be stored uncompressed) check availability first.
 */
expect object DeflateCodec {
  fun isAvailable(): Boolean
  suspend fun compress(bytes: ByteArray): ByteArray
  suspend fun decompress(bytes: ByteArray): ByteArray
}

class DeflateException(message: String, cause: Throwable? = null) :
  Exception(message, cause)
