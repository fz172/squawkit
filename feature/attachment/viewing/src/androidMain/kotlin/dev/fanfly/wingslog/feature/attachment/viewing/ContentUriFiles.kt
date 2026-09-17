package dev.fanfly.wingslog.feature.attachment.viewing

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.fanfly.wingslog.feature.attachment.model.PickedFile

/** Resolves name, type and size for content URIs from the picker or a drop. */
internal fun Context.toPickedFiles(uris: List<Uri>): DroppedFiles {
  val files = uris.mapNotNull { uri ->
    try {
      val mimeType = contentResolver.getType(uri) ?: "*/*"
      val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "file"
      val sizeBytes = contentResolver.openFileDescriptor(uri, "r")
        ?.use { it.statSize } ?: 0L
      PickedFile(
        uri = uri.toString(),
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes
      )
    } catch (e: Exception) {
      // TODO: Log the error.
      null
    }
  }
  return DroppedFiles(files, anyFailed = files.size < uris.size)
}
