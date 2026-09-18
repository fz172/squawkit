package dev.fanfly.wingslog.core.file

/**
 * In-memory file payload to place inside an export ZIP archive.
 */
data class ZipEntryPayload(
  val path: String,
  val bytes: ByteArray,
) {
  init {
    require(path.isNotBlank())
    require(!path.startsWith("/"))
    require(".." !in path.split('/'))
  }
}
