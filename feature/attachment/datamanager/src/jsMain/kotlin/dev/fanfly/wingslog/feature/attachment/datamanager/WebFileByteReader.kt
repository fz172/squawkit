package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.feature.attachment.model.WebPickedFileRegistry

internal class WebFileByteReader : FileByteReader {
  override fun readBytes(uri: String): ByteArray? =
    WebPickedFileRegistry.take(uri)
}
