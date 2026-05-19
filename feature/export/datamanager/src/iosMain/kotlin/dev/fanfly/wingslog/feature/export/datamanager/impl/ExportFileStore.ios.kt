package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
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
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual class ExportFileStore {
  actual suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile =
    withContext(Dispatchers.Default) {
      val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String
      val directory = "$docs/Hopply"
      val fm = NSFileManager.defaultManager
      if (!fm.fileExistsAtPath(directory)) {
        fm.createDirectoryAtPath(directory, true, null, null)
      }
      val path = "$directory/$fileName"
      val data: NSData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
      }
      data.writeToFile(path, atomically = true)
      ExportedFile(
        filePath = path,
        displayLocationKind = ExportDisplayLocation.FILES_HOPPLY,
        sizeBytes = bytes.size.toLong(),
      )
    }
}
