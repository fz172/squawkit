package dev.fanfly.wingslog.feature.export.datamanager.impl

import android.os.Environment
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual class ExportFileStore {
  actual suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile =
    withContext(Dispatchers.IO) {
      val directory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Hopply",
      )
      directory.mkdirs()
      val file = File(directory, fileName)
      file.writeBytes(bytes)
      ExportedFile(
        filePath = file.absolutePath,
        displayLocationKind = ExportDisplayLocation.DOWNLOADS_HOPPLY,
        sizeBytes = file.length(),
      )
    }
}
