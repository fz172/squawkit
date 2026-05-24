package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.export.ExportRecord

/**
 * Platform file sink for finished export archives and their metadata index.
 */
expect class ExportFileStore {
  /**
   * Writes [bytes] as [fileName] and returns the saved archive metadata.
   */
  suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile

  /**
   * Persists [record] in the app-private metadata index so its full scope is rediscoverable when
   * listing history. Keyed by `export_id`; replaces any existing record for the same export.
   */
  suspend fun saveRecord(ownerUid: String, record: ExportRecord)

  /**
   * Lists previously written export archives, newest first, enriched with stored metadata.
   */
  suspend fun listExports(ownerUid: String): List<ExportRecord>

  /**
   * Deletes the export referenced by [exportId] and forgets its metadata. Returns true when an
   * archive was removed.
   */
  suspend fun deleteExport(ownerUid: String, exportId: String): Boolean
}
