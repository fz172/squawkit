package dev.fanfly.wingslog.core.database

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.io.encoding.Base64

@OptIn(ExperimentalForeignApi::class)
actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
  val value = this.get<String?>(field) ?: return null
  return try {
    Base64.decode(value)
  } catch (e: Exception) {
    null
  }
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
  val processedData = data.mapValues { (_, value) ->
    if (value is ByteArray) Base64.encode(value) else value
  }
  this.set(processedData, merge = merge)
}

