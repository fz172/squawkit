package dev.fanfly.wingslog.feature.export.datamanager.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

actual class ExportFileStore(private val context: Context) {
  // Writes through MediaStore so the archive lands in the user-visible Downloads/Hopply
  // folder without requiring storage permissions on Android 10+ (scoped storage).
  actual suspend fun writeZip(fileName: String, bytes: ByteArray): ExportedFile =
    withContext(Dispatchers.IO) {
      val resolver = context.contentResolver
      val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
      val pendingValues = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Hopply")
        put(MediaStore.Downloads.IS_PENDING, 1)
      }
      val uri = resolver.insert(collection, pendingValues)
        ?: throw IOException("Unable to create export entry in Downloads")
      try {
        resolver.openOutputStream(uri)?.use { output ->
          output.write(bytes)
        } ?: throw IOException("Unable to open output stream for $uri")
        val finalize = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(uri, finalize, null, null)
      } catch (t: Throwable) {
        resolver.delete(uri, null, null)
        throw t
      }
      ExportedFile(
        filePath = uri.toString(),
        fileName = fileName,
        displayLocationKind = ExportDisplayLocation.DOWNLOADS_HOPPLY,
        sizeBytes = bytes.size.toLong(),
      )
    }

  actual suspend fun listExports(): List<ExportRecord> =
    withContext(Dispatchers.IO) {
      val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
      val projection = arrayOf(
        MediaStore.Downloads._ID,
        MediaStore.Downloads.DISPLAY_NAME,
        MediaStore.Downloads.SIZE,
        MediaStore.Downloads.DATE_ADDED,
      )
      val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
      val selectionArgs = arrayOf("%${Environment.DIRECTORY_DOWNLOADS}/Hopply/%")
      val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

      val records = mutableListOf<ExportRecord>()
      context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
        ?.use { cursor ->
          val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
          val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
          val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
          val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
          while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            records += ExportRecord(
              filePath = ContentUris.withAppendedId(collection, id).toString(),
              fileName = cursor.getString(nameColumn),
              sizeBytes = cursor.getLong(sizeColumn),
              // DATE_ADDED is stored in seconds since the epoch.
              createdAtEpochMillis = cursor.getLong(dateColumn) * 1_000L,
              displayLocationKind = ExportDisplayLocation.DOWNLOADS_HOPPLY,
            )
          }
        }
      records
    }

  actual suspend fun deleteExport(filePath: String): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        context.contentResolver.delete(filePath.toUri(), null, null) > 0
      }.getOrDefault(false)
    }
}
