package dev.fanfly.wingslog.feature.export.datamanager.impl

internal data class ZipEntryPayload(
  val path: String,
  val bytes: ByteArray,
) {
  init {
    require(path.isNotBlank())
    require(!path.startsWith("/"))
    require(".." !in path.split('/'))
  }
}

internal expect class ZipFileWriter {
  fun write(entries: List<ZipEntryPayload>): ByteArray
}
