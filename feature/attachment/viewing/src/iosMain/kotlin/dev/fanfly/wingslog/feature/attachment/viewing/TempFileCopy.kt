@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.fanfly.wingslog.feature.attachment.viewing

import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.UniformTypeIdentifiers.UTType

/**
 * Copies a picked or dropped file into our temp dir under [name]; null if it can't be read. The
 * [PickedFile.uri] is the absolute copy path, the shape [FileByteReaderImpl] and the camera flow
 * expect.
 */
internal fun copyToTempPickedFile(sourcePath: String, name: String): PickedFile? {
  val destPath = "${NSTemporaryDirectory()}picked_${NSUUID().UUIDString()}_$name"
  val fm = NSFileManager.defaultManager
  fm.removeItemAtPath(destPath, null)
  if (!fm.copyItemAtPath(sourcePath, toPath = destPath, error = null)) return null
  val size = (fm.attributesOfItemAtPath(destPath, null)
    ?.get(NSFileSize) as? NSNumber)
    ?.longLongValue ?: 0L
  return PickedFile(
    uri = destPath,
    name = name,
    mimeType = mimeTypeForName(name),
    sizeBytes = size,
  )
}

/** Best-effort MIME from a filename's extension via the system UTType database. */
private fun mimeTypeForName(name: String): String {
  val ext = name.substringAfterLast('.', "")
  if (ext.isEmpty()) return "application/octet-stream"
  return UTType.typeWithFilenameExtension(ext)?.preferredMIMEType
    ?: "application/octet-stream"
}
