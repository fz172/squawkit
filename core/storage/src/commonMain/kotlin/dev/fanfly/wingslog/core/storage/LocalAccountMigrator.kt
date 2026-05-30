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
          db.schemaQueries.reassignEntities(newPrefix, remainderStart, oldPrefixLike)
          db.schemaQueries.reassignBlobs(newPrefix, remainderStart, oldPrefixLike)
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
  }
}
