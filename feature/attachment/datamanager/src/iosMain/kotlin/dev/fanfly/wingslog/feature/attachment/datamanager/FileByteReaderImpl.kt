package dev.fanfly.wingslog.feature.attachment.datamanager

/** iOS file reading. File picker not implemented on iOS yet, so this is not exercised. */
class FileByteReaderImpl : FileByteReader {
  override fun readBytes(uri: String): ByteArray? = null
}
