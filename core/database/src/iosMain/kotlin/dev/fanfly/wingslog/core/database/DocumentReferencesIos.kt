package dev.fanfly.wingslog.core.database

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow
import platform.Foundation.NSData

actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
//  val value = this.get(field) ?: return null
//  if (value is NSData) {
//    return (value as NSData).toByteArray()
//  }
//  if (value is ByteArray) {
//    return value
//  }
  return null
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
  this.set(data, merge = merge)
}
