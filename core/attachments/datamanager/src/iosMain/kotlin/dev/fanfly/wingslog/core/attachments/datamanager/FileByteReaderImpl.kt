package dev.fanfly.wingslog.core.attachments.datamanager

/** iOS file reading. V1: file picker not implemented on iOS, so this is not called in practice. */
class FileByteReaderImpl : FileByteReader {
  override fun readBytes(uri: String): ByteArray? = null
}
