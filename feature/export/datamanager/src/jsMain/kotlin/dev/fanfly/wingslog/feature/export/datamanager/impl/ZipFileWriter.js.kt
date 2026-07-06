package dev.fanfly.wingslog.feature.export.datamanager.impl

actual class ZipFileWriter {
  actual fun write(entries: List<ZipEntryPayload>): ByteArray =
    StoredZipArchive.build(entries)
}
