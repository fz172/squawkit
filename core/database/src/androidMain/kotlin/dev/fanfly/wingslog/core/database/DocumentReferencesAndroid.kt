package dev.fanfly.wingslog.core.database

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.SetOptions.merge
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android
import kotlinx.coroutines.tasks.await
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.QuerySnapshot
actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
  // GitLive KMP SDK android extension property "android" gives the native com.google.firebase.firestore.DocumentSnapshot
  return this.android.getBlob(field)?.toBytes()
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
  val processedData = data.mapValues { (_, value) ->
    if (value is ByteArray) Blob.fromBytes(value) else value
  }
  if (merge) {
    this.android.set(processedData, merge()).await()
  } else {
    this.android.set(processedData).await()
  }
}

actual fun DocumentReference.observeSnapshot(): kotlinx.coroutines.flow.Flow<DocumentSnapshot> {
    return this.snapshots
}

actual fun Query.observeSnapshot(): kotlinx.coroutines.flow.Flow<QuerySnapshot> {
    return this.snapshots
}
