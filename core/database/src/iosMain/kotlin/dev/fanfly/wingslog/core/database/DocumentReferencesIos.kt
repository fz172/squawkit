package dev.fanfly.wingslog.core.database

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

private val logger = Logger.withTag("DocumentSnapshotIos")

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
  val bytes = ByteArray(length.toInt())
  if (length > 0u) {
    bytes.usePinned {
      memcpy(it.addressOf(0), this.bytes, length)
    }
  }
  return bytes
}

actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
  val value = this.get(field) ?: return null
  logger.d { "Document loaded" }
  if (value is NSData) {
    return (value as NSData).toByteArray()
  }
  if (value is ByteArray) {
    return value
  }
  return null
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
  this.set(data, merge = merge)
}

