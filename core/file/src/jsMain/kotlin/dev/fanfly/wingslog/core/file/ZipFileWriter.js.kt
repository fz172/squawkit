package dev.fanfly.wingslog.core.file

actual class ZipFileWriter {
  actual suspend fun write(entries: List<ZipEntryPayload>): ByteArray =
    CommonZipArchive.build(entries)
}
