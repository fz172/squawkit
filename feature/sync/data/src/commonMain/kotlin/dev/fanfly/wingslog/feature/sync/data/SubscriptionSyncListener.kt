package dev.fanfly.wingslog.feature.sync.data

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.catch
import kotlin.time.Clock

/**
 * Mirrors the server-authoritative entitlement doc at top-level `subscriptions/{uid}` into the local
 * store as a read-only [CollectionKind.Subscription] row.
 *
 * The entitlement cannot ride the ordinary user-tree sync: it lives outside `users/{uid}/` precisely
 * so the rules can deny client writes (a doc in the user tree would be forgeable — a paywall bypass;
 * see docs/subscription/subscription_design.html §3). So this is a dedicated single-doc snapshot
 * listener rather than a hydrated collection.
 *
 * It is the **only** local writer of that row and always writes `dirty = false`, so the row is never
 * picked up by [PushWorker] — read-only, one way, server → device. [SyncEngine] runs it inside the
 * per-user scope, so the subscription detaches on sign-out with everything else.
 */
class SubscriptionSyncListener(
  private val firestore: FirebaseFirestore,
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock,
) {

  private val log = Logger.withTag(TAG)

  /** Collects the entitlement doc for [uid] until the surrounding scope is cancelled. */
  suspend fun run(uid: String) {
    firestore.collection(COLLECTION).document(uid).snapshots
      .catch { e -> log.w(e) { "subscription snapshot stream failed; entitlement stays cached" } }
      .collect { snap ->
        runCatching { apply(uid, snap) }
          .onFailure { log.w(it) { "failed to apply entitlement snapshot for this account" } }
      }
  }

  private suspend fun apply(uid: String, snap: DocumentSnapshot) {
    // Decode outside the write lock; only the DB write needs it held.
    val proto: Subscription? = if (snap.exists) snap.data<SubscriptionDocWire>().toProto() else null
    val scopePath = EntityScope.userRoot(uid).toPath()
    val now = Clock.System.now().toEpochMilliseconds()

    writeLock.withLock {
      db.transaction {
        db.schemaQueries.upsert(
          collection = CollectionKind.Subscription,
          scope_path = scopePath,
          id = DOC_ID,
          // Absent doc → tombstone the cache so the manager resolves to FREE. A present doc → the
          // encoded entitlement.
          payload = proto?.let { Subscription.ADAPTER.encode(it) } ?: ByteArray(0),
          payload_schema = CollectionKind.Subscription.schemaName,
          updated_at = now,
          remote_updated_at = now,
          dirty = false,
          deleted = proto == null,
          writer_uid = null,
        )
      }
    }
  }

  companion object {
    private const val TAG = "SubscriptionSyncListener"

    /** Top-level, function-only entitlement collection (never under the user tree). */
    private const val COLLECTION = "subscriptions"

    /** The single local row id; the account's entitlement is one doc. */
    private const val DOC_ID = "main"
  }
}
