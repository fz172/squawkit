package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS [BlobFilesystem]. Root is `NSDocumentDirectory/blobs/`; the directory is excluded from
 * iCloud backup so attachments don't double-replicate (we already round-trip them through
 * Firebase Storage).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class NsBlobFilesystem : BlobFilesystem {

  private val rootPath: String = run {
    val docs = NSSearchPathForDirectoriesInDomains(
      NSDocumentDirectory,
      NSUserDomainMask,
      true
    )
      .first() as String
    val root = "$docs/blobs"
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(root)) {
      fm.createDirectoryAtPath(root, true, null, null)
      val url = NSURL.fileURLWithPath(root, isDirectory = true)
      url.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    }
    root
  }

  override suspend fun write(relativePath: String, bytes: ByteArray) {
    withContext(Dispatchers.Default) {
      val abs = absolutePath(relativePath)
      ensureParent(abs)
      val nsData: NSData = bytes.usePinned { pinned ->
        NSData.create(
          bytes = pinned.addressOf(0),
          length = bytes.size.toULong()
        )
      }
      nsData.writeToFile(abs, atomically = true)
    }
  }

  override suspend fun read(relativePath: String): ByteArray =
    withContext(Dispatchers.Default) {
      val data = NSData.dataWithContentsOfFile(absolutePath(relativePath))
        ?: error("blob not found: $relativePath")
      val len = data.length.toInt()
      val out = ByteArray(len)
      if (len > 0) {
        out.usePinned { pinned ->
          platform.posix.memcpy(pinned.addressOf(0), data.bytes, len.toULong())
        }
      }
      out
    }

  override suspend fun delete(relativePath: String) {
    withContext(Dispatchers.Default) {
      NSFileManager.defaultManager.removeItemAtPath(
        absolutePath(relativePath),
        null
      )
    }
  }

  override suspend fun exists(relativePath: String): Boolean =
    withContext(Dispatchers.Default) {
      NSFileManager.defaultManager.fileExistsAtPath(absolutePath(relativePath))
    }

  override fun uriFor(relativePath: String): String =
    NSURL.fileURLWithPath(absolutePath(relativePath)).absoluteString
      ?: "file://${absolutePath(relativePath)}"

  private fun absolutePath(relativePath: String): String =
    "$rootPath/$relativePath"

  private fun ensureParent(absolute: String) {
    val parent = absolute.substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isNotEmpty()) {
      val fm = NSFileManager.defaultManager
      if (!fm.fileExistsAtPath(parent)) {
        fm.createDirectoryAtPath(parent, true, null, null)
      }
    }
  }
}
