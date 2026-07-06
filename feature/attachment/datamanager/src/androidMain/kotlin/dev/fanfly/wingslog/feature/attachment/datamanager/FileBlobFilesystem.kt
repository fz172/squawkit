package dev.fanfly.wingslog.feature.attachment.datamanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [BlobFilesystem] backed by `java.io.File`. Used in production on Android (root =
 * `context.filesDir`) and in JVM unit tests (root = a `@TempDir`).
 */
class FileBlobFilesystem(private val rootDir: File) : BlobFilesystem {

  init {
    rootDir.mkdirs()
  }

  override suspend fun write(relativePath: String, bytes: ByteArray) {
    withContext(Dispatchers.IO) {
      val file = file(relativePath)
      file.parentFile?.mkdirs()
      file.writeBytes(bytes)
    }
  }

  override suspend fun read(relativePath: String): ByteArray =
    withContext(Dispatchers.IO) { file(relativePath).readBytes() }

  override suspend fun delete(relativePath: String) {
    withContext(Dispatchers.IO) { file(relativePath).delete() }
  }

  override suspend fun exists(relativePath: String): Boolean =
    withContext(Dispatchers.IO) { file(relativePath).isFile }

  override fun uriFor(relativePath: String): String = file(relativePath).toURI()
    .toString()

  private fun file(relativePath: String): File = File(rootDir, relativePath)
}
