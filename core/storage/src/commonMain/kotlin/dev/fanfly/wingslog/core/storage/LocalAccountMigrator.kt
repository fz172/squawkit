package dev.fanfly.wingslog.core.storage

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.withContext

/**
 * Re-keys this device's local data from one user (UID) to another. Used by the account-merge path
 * of the guest → account upgrade: when a guest links into an account that already exists, the app
 * signs in to that account (a new UID) and calls [reassign] to bring the guest's on-device records
 * along. See docs/account/account_upgrade_design.html §6.
 */
interface LocalAccountMigrator {
  /**
   * Moves every local entity and blob owned by [fromUid] into [toUid]'s scope, marks entities dirty
   * (and clears their remote timestamps) so the sync engine re-uploads them under the destination
   * account, resets blobs to re-upload, and drops both UIDs' sync cursors so the destination
   * re-hydrates its existing cloud set.
   *
   * The merge is additive for *content* (both record sets survive, per design §5), but the
   * destination account keeps its own *device/identity singletons* (UserInfo, DeveloperOptions): the
   * guest's copies are dropped rather than moved (a guest's experimental flags are not inherited
   * into a real account). Guest records that would land on an id the destination already holds are
   * dropped for the same reason — see [reassign]'s SQL and [LocalAccountMigratorImpl.DROPPED_ON_MERGE].
   *
   * Idempotent: once moved, no rows match [fromUid] anymore, so a re-run is a no-op. This makes the
   * merge crash-safe (a relaunch can re-run it). No-op when the UIDs are equal or blank.
   */
  suspend fun reassign(fromUid: String, toUid: String)
}

class LocalAccountMigratorImpl(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) : LocalAccountMigrator {

  private val log = Logger.withTag(TAG)

  override suspend fun reassign(fromUid: String, toUid: String) {
    if (fromUid.isBlank() || toUid.isBlank() || fromUid == toUid) return
    val oldPrefix = "/users/$fromUid/"
    val newPrefix = "/users/$toUid/"
    val oldPrefixLike = "$oldPrefix%"
    // 1-based index of the path tail that follows the old prefix (SQLite substr is 1-based).
    val remainderStart = (oldPrefix.length + 1).toString()
    withContext(storageIoContext) {
      writeLock.withLock {
        db.schemaQueries.transaction {
          // Per-user singletons live at a fixed id under the user root, so the destination account
          // already has its own — they are dropped from the guest scope, never moved. Moving one
          // would collide on the primary key (both sit at the same id) AND override the account's
          // own copy: its identity (UserInfo → self-technician) or its device toggles (DeveloperOptions).
          // A guest's experimental flags are not something to inherit into a real account.
          for (kind in DROPPED_ON_MERGE) {
            db.schemaQueries.deleteCollectionInScopePrefix(kind, oldPrefixLike)
          }
          // Anything else already present at the destination id likewise stays as the destination
          // has it; without this the UPDATE below trips the (collection, scope_path, id) primary key.
          db.schemaQueries.deleteReassignConflicts(
            oldPrefixLike,
            newPrefix,
            remainderStart
          )
          db.schemaQueries.reassignEntities(
            newPrefix,
            remainderStart,
            oldPrefixLike
          )
          db.schemaQueries.reassignBlobs(
            newPrefix,
            remainderStart,
            oldPrefixLike
          )
          // Drop both users' cursors so the destination account re-hydrates its existing cloud set.
          db.schemaQueries.deleteSyncCursorsForUser(fromUid)
          db.schemaQueries.deleteSyncCursorsForUser(toUid)
        }
      }
    }
    log.i { "reassign: moved local data from uid=$fromUid to uid=$toUid" }
  }

  companion object {
    private const val TAG = "LocalAccountMigrator"

    /**
     * Per-user singletons stored at a fixed id directly under the user root: the destination
     * account keeps its own, and the guest's copy is dropped rather than moved. Any new collection
     * of this shape (fixed id at `/users/{uid}/`) must be added here — otherwise the merge trips the
     * (collection, scope_path, id) primary key when both accounts' copies are on the device, and
     * silently overrides the account's copy when only the guest's is. [deleteReassignConflicts]
     * covers the both-on-device case for these too, but not the guest-only case (nothing to conflict
     * with), so identity/toggle collections must be listed explicitly.
     */
    private val DROPPED_ON_MERGE: List<CollectionKind> = listOf(
      CollectionKind.UserInfo,
      CollectionKind.DeveloperOptions,
    )
  }
}
