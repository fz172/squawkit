package dev.fanfly.wingslog.core.file

/**
 * Platform ZIP archive writer.
 */
expect class ZipFileWriter() {
  /**
   * Packages [entries] into a ZIP archive and returns the archive bytes. Suspending because the
   * common-code builder compresses through a codec that is a stream on the web.
   */
  suspend fun write(entries: List<ZipEntryPayload>): ByteArray
}
