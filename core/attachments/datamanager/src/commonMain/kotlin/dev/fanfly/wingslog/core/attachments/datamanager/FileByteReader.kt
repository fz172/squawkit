package dev.fanfly.wingslog.core.attachments.datamanager

/**
 * Reads raw bytes from a platform-specific file URI returned by the file picker.
 * Android: reads via ContentResolver.
 * iOS: reads from the file path.
 */
interface FileByteReader {
  fun readBytes(uri: String): ByteArray?
}
