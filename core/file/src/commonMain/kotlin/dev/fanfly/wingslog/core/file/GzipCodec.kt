package dev.fanfly.wingslog.core.file

/**
 * Gzip for the stored raw file (design §5.2). Suspending because the web implementation is a
 * stream; the others hop to a background dispatcher for the same reason. Where [isAvailable] is
 * false the importer stores `DATA_LOG_ENCODING_NONE` and readers branch on the record's encoding.
 */
expect object GzipCodec {
  fun isAvailable(): Boolean
  suspend fun compress(bytes: ByteArray): ByteArray
  suspend fun decompress(bytes: ByteArray): ByteArray
}

class GzipException(message: String, cause: Throwable? = null) : Exception(message, cause)
