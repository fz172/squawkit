package dev.fanfly.wingslog.feature.attachment.datamanager

import android.content.Context
import android.net.Uri

class FileByteReaderImpl(private val context: Context) : FileByteReader {
  override fun readBytes(uri: String): ByteArray? = try {
    context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
  } catch (e: Exception) {
    null
  }
}
