package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** iOS file reading — [uri] is an absolute filesystem path, e.g. from camera capture. */
class FileByteReaderImpl : FileByteReader {
  @OptIn(ExperimentalForeignApi::class)
  override fun readBytes(uri: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(uri) ?: return null
    val length = data.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
      bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, length.toULong())
      }
    }
    return bytes
  }
}
