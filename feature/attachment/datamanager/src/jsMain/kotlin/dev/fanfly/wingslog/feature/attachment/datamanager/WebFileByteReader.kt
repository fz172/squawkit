package dev.fanfly.wingslog.feature.attachment.datamanager

internal class WebFileByteReader : FileByteReader {
  override fun readBytes(uri: String): ByteArray? =
    WebPickedFileRegistry.take(uri)
}
