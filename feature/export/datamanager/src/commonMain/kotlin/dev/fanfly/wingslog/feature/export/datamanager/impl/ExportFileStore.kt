package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Platform file sink for finished export archives.
 */
expect class ExportFileStore() {
  /**
   * Writes [bytes] as [fileName] and returns the saved archive metadata.
   */
  suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile
}
