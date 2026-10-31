package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
actual fun rememberDataLogFileDownloader(): DataLogFileDownloader {
  val context = LocalContext.current
  return remember(context) { AndroidDataLogFileDownloader(context) }
}

private class AndroidDataLogFileDownloader(
  private val context: Context,
) : DataLogFileDownloader {

  override suspend fun download(
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean = withContext(Dispatchers.IO) {
    val bytes = fetchBytes() ?: return@withContext false
    val resolver = context.contentResolver
    val collection =
      MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val mimeType =
      if (fileName.endsWith(".csv", ignoreCase = true)) "text/csv" else "application/octet-stream"
    val pendingValues = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, fileName)
      put(MediaStore.Downloads.MIME_TYPE, mimeType)
      put(
        MediaStore.Downloads.RELATIVE_PATH,
        "${Environment.DIRECTORY_DOWNLOADS}/SquawkIt"
      )
      put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, pendingValues)
      ?: return@withContext false
    try {
      resolver.openOutputStream(uri)?.use { output ->
        output.write(bytes)
      } ?: throw IOException("Unable to open output stream for $uri")
      val finalize = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
      resolver.update(uri, finalize, null, null)
      true
    } catch (t: Throwable) {
      resolver.delete(uri, null, null)
      false
    }
  }
}
