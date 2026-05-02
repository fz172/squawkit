package dev.fanfly.wingslog.core.sync

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * One-shot read of a Firestore collection. Used by [HydrationRunner].
 *
 * Documents whose `updated_at` is still server-pending (i.e. our own write echoed via local
 * cache before the server stamps it) are skipped — there's no useful timestamp to record yet, and
 * the same row will be redelivered through the live snapshot listener once stamped.
 */
class FirestoreRemoteFetcher(private val firestore: FirebaseFirestore) : RemoteFetcher {

  private val log = Logger.withTag(TAG)

  override suspend fun fetchAll(kind: CollectionKind, scope: EntityScope): List<RemoteEntity> {
    val coll = FirestoreRefs.collection(firestore, kind, scope)
    val snap = coll.get()
    return snap.documents.mapNotNull { decodeOrNull(it) }
  }

  private fun decodeOrNull(doc: DocumentSnapshot): RemoteEntity? = runCatching {
    decodeRemoteEntity(doc)
  }.getOrElse { e ->
    log.w(e) { "skipping malformed sync doc ${doc.id}" }
    null
  }

  companion object {
    private const val TAG = "FirestoreRemoteFetcher"
  }
}

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeRemoteEntity(doc: DocumentSnapshot): RemoteEntity? {
  val wire = doc.data<SyncDocWire>()
  val tsMs = wire.updated_at.epochMsOrNull() ?: return null
  return RemoteEntity(
    id = doc.id,
    payload = Base64.decode(wire.payload),
    deleted = wire.deleted,
    remoteTsMs = tsMs,
  )
}
