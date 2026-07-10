package dev.fanfly.wingslog.feature.sync.data.impl

import dev.fanfly.wingslog.feature.sync.data.SyncDocWire
import dev.fanfly.wingslog.feature.sync.data.SyncWrite
import dev.fanfly.wingslog.feature.sync.data.SyncWriter
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Writes one entity revision to Firestore using GitLive's commonMain `set(value)` overload, which
 * walks the kotlinx-serialization graph and delegates platform glue (server timestamp, primitives)
 * to GitLive. No expect/actual needed at this layer.
 */
class FirestoreSyncWriter(private val firestore: FirebaseFirestore) :
  SyncWriter {

  @OptIn(ExperimentalEncodingApi::class)
  override suspend fun push(write: SyncWrite) {
    val ref = FirestoreRefs.document(
      firestore,
      write.kind,
      write.scope,
      write.id
    )
    ref.set(
      SyncDocWire(
        payload = Base64.encode(write.payload),
        deleted = write.deleted,
        schema = write.schema,
        lastUpdateTimestamp = Timestamp.ServerTimestamp,
        writerUid = write.writerUid,
      ),
    )
  }
}
