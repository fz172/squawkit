package dev.fanfly.wingslog.feature.attachment.datamanager

/**
 * Browser file picker selections are not stable filesystem handles. The picker reads selected
 * bytes eagerly, stores them here, and hands common code an opaque `web-picked:{id}` URI.
 */
internal object WebPickedFileRegistry {
  private const val PREFIX = "web-picked:"
  private var nextId = 0
  private val bytesByUri = mutableMapOf<String, ByteArray>()

  fun put(bytes: ByteArray): String {
    val uri = "$PREFIX${nextId++}"
    bytesByUri[uri] = bytes
    return uri
  }

  fun take(uri: String): ByteArray? =
    bytesByUri.remove(uri)

  fun clear(uri: String) {
    bytesByUri.remove(uri)
  }
}
