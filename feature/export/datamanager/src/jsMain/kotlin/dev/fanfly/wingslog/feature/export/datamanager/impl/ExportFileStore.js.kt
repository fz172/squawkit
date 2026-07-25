package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import kotlinx.browser.localStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Web [ExportFileStore]. Browsers have no app-private archive directory, so a finished export's
 * bytes are cached in memory (keyed by file name) until the user explicitly downloads it, at which
 * point they're streamed to the user's Downloads via a temporary object URL + programmatic anchor
 * click (see [ExportFileDownloader]).
 *
 * The history index (export scope/metadata) is persisted in `localStorage`, base64-encoded. The
 * in-memory byte cache does not survive a page reload, so a later download for an already-synced
 * export falls back to the cloud copy (see `ExportManager.downloadArchiveBytes`); [listExports]
 * returns the stored index verbatim and [deleteExport] only forgets the metadata.
 */
@OptIn(ExperimentalEncodingApi::class)
actual class ExportFileStore {

  private val cachedArchives = mutableMapOf<String, ByteArray>()

  actual suspend fun writeZip(
    fileName: String,
    bytes: ByteArray
  ): ExportedFile {
    cachedArchives[fileName] = bytes
    return ExportedFile(
      // No durable, app-reachable path exists on the web; the file name is the only stable handle.
      filePath = fileName,
      fileName = fileName,
      displayLocationKind = ExportDisplayLocation.DOWNLOADS_SQUAWKIT,
      sizeBytes = bytes.size.toLong(),
    )
  }

  actual suspend fun readBytes(filePath: String): ByteArray? = cachedArchives[filePath]

  actual suspend fun saveRecord(ownerUid: String, record: ExportRecord) {
    writeIndex(
      ownerUid,
      ExportRecordManifest.upsert(readIndex(ownerUid), record)
    )
  }

  actual suspend fun listExports(ownerUid: String): List<ExportRecord> =
    readIndex(ownerUid)

  actual suspend fun deleteExport(ownerUid: String, exportId: String): Boolean {
    val stored = readIndex(ownerUid)
    if (stored.none { it.export_id == exportId }) return false
    writeIndex(ownerUid, ExportRecordManifest.remove(stored, exportId))
    return true
  }

  private fun storageKey(ownerUid: String): String =
    "export_record_index_${ownerUid.replace(Regex("[^A-Za-z0-9._-]"), "_")}"

  private fun readIndex(ownerUid: String): List<ExportRecord> {
    val encoded =
      localStorage.getItem(storageKey(ownerUid)) ?: return emptyList()
    val bytes = runCatching { Base64.decode(encoded) }.getOrNull()
    return ExportRecordManifest.decode(bytes)
  }

  private fun writeIndex(ownerUid: String, records: List<ExportRecord>) {
    localStorage.setItem(
      storageKey(ownerUid),
      Base64.encode(ExportRecordManifest.encode(records))
    )
  }
}
