package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportRecord
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class ExportFileStore {
  private val exportDirectory: String
    get() = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
      .first() as String + "/Hopply"

  actual suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile =
    withContext(Dispatchers.Default) {
      val directory = exportDirectory
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
        fileName = fileName,
        displayLocationKind = ExportDisplayLocation.FILES_HOPPLY,
        sizeBytes = bytes.size.toLong(),
      )
    }

  actual suspend fun listExports(): List<ExportRecord> =
    withContext(Dispatchers.Default) {
      val directory = exportDirectory
      val fm = NSFileManager.defaultManager
      val names = fm.contentsOfDirectoryAtPath(directory, null).orEmpty()
      names.filterIsInstance<String>()
        .filter { it.endsWith(".zip") }
        .map { name ->
          val path = "$directory/$name"
          val attributes = fm.attributesOfItemAtPath(path, null)
          val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
          val modified = (attributes?.get(NSFileModificationDate) as? NSDate)
            ?.timeIntervalSince1970 ?: 0.0
          ExportRecord(
            filePath = path,
            fileName = name,
            sizeBytes = size,
            createdAtEpochMillis = (modified * 1_000).toLong(),
            displayLocationKind = ExportDisplayLocation.FILES_HOPPLY,
          )
        }
        .sortedByDescending { it.createdAtEpochMillis }
    }

  actual suspend fun deleteExport(filePath: String): Boolean =
    withContext(Dispatchers.Default) {
      NSFileManager.defaultManager.removeItemAtPath(filePath, null)
    }
}
