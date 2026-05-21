package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.memcpy
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
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class ExportFileStore {
  private val exportDirectory: String
    get() = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
      .first() as String + "/Hopply"

  // App-private metadata index. The leading dot keeps it out of the ".zip" archive listing and
  // tucked away from the Files app; the archives are the source of truth for existence, this is
  // the source of truth for scope (formats / date range / aircraft).
  private val indexPath: String
    get() = "$exportDirectory/.export_record_index.pb"

  actual suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile =
    withContext(Dispatchers.Default) {
      ensureDirectory()
      val path = "$exportDirectory/$fileName"
      writeBytes(path, bytes)
      ExportedFile(
        filePath = path,
        fileName = fileName,
        displayLocationKind = ExportDisplayLocation.FILES_HOPPLY,
        sizeBytes = bytes.size.toLong(),
      )
    }

  actual suspend fun saveRecord(record: ExportRecord): Unit =
    withContext(Dispatchers.Default) {
      ensureDirectory()
      writeBytes(indexPath, ExportRecordManifest.encode(ExportRecordManifest.upsert(readIndex(), record)))
    }

  actual suspend fun listExports(): List<ExportRecord> =
    withContext(Dispatchers.Default) {
      val reconciled = ExportRecordManifest.reconcile(readIndex(), discoverArchives())
      ensureDirectory()
      writeBytes(indexPath, ExportRecordManifest.encode(reconciled))
      reconciled
    }

  actual suspend fun deleteExport(exportId: String): Boolean =
    withContext(Dispatchers.Default) {
      val reconciled = ExportRecordManifest.reconcile(readIndex(), discoverArchives())
      val record = reconciled.firstOrNull { it.export_id == exportId }
      val removed = record?.file_path?.takeIf { it.isNotBlank() }?.let { filePath ->
        NSFileManager.defaultManager.removeItemAtPath(filePath, null)
      } ?: false
      writeBytes(indexPath, ExportRecordManifest.encode(ExportRecordManifest.remove(reconciled, exportId)))
      removed
    }

  private fun discoverArchives(): List<LocalArchiveRecord> {
    val fm = NSFileManager.defaultManager
    val names = fm.contentsOfDirectoryAtPath(exportDirectory, null).orEmpty()
    return names.filterIsInstance<String>()
      .filter { it.endsWith(".zip") }
      .map { name ->
        val path = "$exportDirectory/$name"
        val attributes = fm.attributesOfItemAtPath(path, null)
        val size = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        val modified = (attributes?.get(NSFileModificationDate) as? NSDate)
          ?.timeIntervalSince1970 ?: 0.0
        LocalArchiveRecord(
          filePath = path,
          fileName = name,
          sizeBytes = size,
          createdAtEpochMillis = (modified * 1_000).toLong(),
          displayLocation = ExportDisplayLocation.FILES_HOPPLY,
        )
      }
  }

  private fun ensureDirectory() {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(exportDirectory)) {
      fm.createDirectoryAtPath(exportDirectory, true, null, null)
    }
  }

  private fun readIndex(): List<ExportRecord> =
    ExportRecordManifest.decode(NSData.dataWithContentsOfFile(indexPath)?.toByteArray())

  private fun writeBytes(path: String, bytes: ByteArray) {
    val data: NSData = bytes.usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    data.writeToFile(path, atomically = true)
  }

  private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length.convert()) }
    return out
  }
}
