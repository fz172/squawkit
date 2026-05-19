package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation

internal data class ExportedFile(
  val filePath: String,
  val displayLocationKind: ExportDisplayLocation,
  val sizeBytes: Long,
)

internal expect class ExportFileStore {
  suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile
}
