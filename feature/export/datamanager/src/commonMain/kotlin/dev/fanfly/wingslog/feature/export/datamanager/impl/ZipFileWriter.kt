package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Platform ZIP archive writer.
 */
expect class ZipFileWriter() {
  /**
   * Packages [entries] into a ZIP archive and returns the archive bytes.
   */
  fun write(entries: List<ZipEntryPayload>): ByteArray
}
