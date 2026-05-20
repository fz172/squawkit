package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportRecord

/**
 * Platform file sink for finished export archives.
 */
expect class ExportFileStore {
  /**
   * Writes [bytes] as [fileName] and returns the saved archive metadata.
   */
  suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile

  /**
   * Lists previously written export archives, newest first.
   */
  suspend fun listExports(): List<ExportRecord>

  /**
   * Deletes the export referenced by [filePath]. Returns true when an archive was removed.
   */
  suspend fun deleteExport(filePath: String): Boolean
}
