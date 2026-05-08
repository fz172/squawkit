package dev.fanfly.wingslog.feature.attachment.datamanager

/**
 * Reads raw bytes from a platform-specific file URI returned by the file picker.
 *
 * Android: reads via `ContentResolver` (content URIs).
 * iOS: reads from the file path.
 *
 * Returns `null` when the URI cannot be opened (file removed, permission revoked, etc.).
 */
interface FileByteReader {
  fun readBytes(uri: String): ByteArray?
}
