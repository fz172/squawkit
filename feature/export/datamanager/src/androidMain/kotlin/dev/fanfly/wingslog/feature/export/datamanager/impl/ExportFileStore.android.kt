package dev.fanfly.wingslog.feature.export.datamanager.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

actual class ExportFileStore(private val context: Context) {
  // App-private index of export metadata. Survives alongside the user-visible archives in
  // Downloads/Hopply; the archives are the source of truth for existence, this is the source of
  // truth for the scope (formats / date range / aircraft) that can't be read back off the file.
  private val indexFile: File
    get() = File(context.filesDir, "export_record_index.pb")

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

  actual suspend fun saveRecord(record: ExportRecord): Unit =
    withContext(Dispatchers.IO) {
      writeIndex(ExportRecordManifest.upsert(readIndex(), record))
    }

  actual suspend fun listExports(): List<ExportRecord> =
    withContext(Dispatchers.IO) {
      val reconciled = ExportRecordManifest.reconcile(readIndex(), discoverArchives())
      writeIndex(reconciled)
      reconciled
    }

  actual suspend fun deleteExport(exportId: String): Boolean =
    withContext(Dispatchers.IO) {
      val reconciled = ExportRecordManifest.reconcile(readIndex(), discoverArchives())
      val record = reconciled.firstOrNull { it.export_id == exportId }
      val removed = record?.file_path?.takeIf { it.isNotBlank() }?.let { filePath ->
        runCatching {
          context.contentResolver.delete(filePath.toUri(), null, null) > 0
        }.getOrDefault(false)
      } ?: false
      writeIndex(ExportRecordManifest.remove(reconciled, exportId))
      removed
    }

  private fun discoverArchives(): List<ExportRecord> {
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
            export_id = "",
            file_path = ContentUris.withAppendedId(collection, id).toString(),
            file_name = cursor.getString(nameColumn),
            size_bytes = cursor.getLong(sizeColumn),
            // DATE_ADDED is stored in seconds since the epoch.
            created_at_epoch_millis = cursor.getLong(dateColumn) * 1_000L,
            display_location = ExportDisplayLocation.DOWNLOADS_HOPPLY.name,
          )
        }
      }
    return records
  }

  private fun readIndex(): List<ExportRecord> =
    runCatching { indexFile.takeIf { it.exists() }?.readBytes() }
      .getOrNull()
      .let(ExportRecordManifest::decode)

  private fun writeIndex(records: List<ExportRecord>) {
    runCatching { indexFile.writeBytes(ExportRecordManifest.encode(records)) }
  }
}
