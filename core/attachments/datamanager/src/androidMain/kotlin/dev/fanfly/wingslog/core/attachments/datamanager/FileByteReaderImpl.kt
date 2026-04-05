package dev.fanfly.wingslog.core.attachments.datamanager

import android.content.Context
import androidx.core.net.toUri

class FileByteReaderImpl(private val context: Context) : FileByteReader {
  override fun readBytes(uri: String): ByteArray? = try {
    context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() }
  } catch (e: Exception) {
    null
  }
}
