package dev.fanfly.wingslog.feature.export.datamanager.impl

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual class ZipFileWriter {
  actual fun write(entries: List<ZipEntryPayload>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      entries.forEach { entry ->
        zip.putNextEntry(ZipEntry(entry.path))
        zip.write(entry.bytes)
        zip.closeEntry()
      }
    }
    return output.toByteArray()
  }
}
