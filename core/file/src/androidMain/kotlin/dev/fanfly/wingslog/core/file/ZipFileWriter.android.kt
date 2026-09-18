package dev.fanfly.wingslog.core.file

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual class ZipFileWriter {
  actual suspend fun write(entries: List<ZipEntryPayload>): ByteArray =
    withContext(Dispatchers.Default) {
      val output = ByteArrayOutputStream()
      ZipOutputStream(output).use { zip ->
        entries.forEach { entry ->
          zip.putNextEntry(ZipEntry(entry.path))
          zip.write(entry.bytes)
          zip.closeEntry()
        }
      }
      output.toByteArray()
    }
}
