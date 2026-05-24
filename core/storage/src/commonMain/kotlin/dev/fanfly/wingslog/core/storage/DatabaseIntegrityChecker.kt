package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Runs `PRAGMA integrity_check` on the local SQLite database at startup. If the check fails,
 * [DatabaseHealth.isCorrupted] is `true` and the UI shows a recovery dialog (PRD §10).
 *
 * The [SqlDriver] is accepted directly (in addition to [WingsLogDatabase]) because
 * [WingsLogDatabase] is a generated interface that does not expose the driver.
 *
 * [wipeAllData] clears entity, sync_cursor, and blob_object rows so the sync engine can
 * re-hydrate from Firestore on next sign-in. sync_config is kept so user preferences survive.
 */
class DatabaseIntegrityChecker(
  private val db: WingsLogDatabase,
  private val driver: SqlDriver,
) {

  private val log = Logger.withTag(TAG)

  fun checkIntegrity(): Boolean {
    return try {
      val result = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA integrity_check(1)",
        mapper = { cursor: SqlCursor ->
          QueryResult.Value(cursor.next().value && cursor.getString(0) == "ok")
        },
        parameters = 0,
      ).value
      if (!result) log.e { "PRAGMA integrity_check returned non-ok result" }
      result
    } catch (e: Exception) {
      log.e(e) { "PRAGMA integrity_check threw an exception" }
      false
    }
  }

  suspend fun wipeAllData() {
    try {
      db.schemaQueries.wipeAllBlobObjects()
      db.schemaQueries.wipeAllSyncCursors()
      db.schemaQueries.wipeAllEntities()
      log.i { "wipeAllData: cleared entity, sync_cursor, and blob_object tables" }
    } catch (e: Exception) {
      log.e(e) { "wipeAllData failed" }
    }
  }

  /**
   * Deletes entity rows and sync cursors for [uid] so the sync engine re-hydrates from Firestore
   * on next sign-in. blob_object rows and files are handled separately by
   * [AttachmentManager.wipeLocalData]. Call this from the sign-out path before clearing auth state.
   */
  suspend fun wipeDataForUser(uid: String) {
    val scopePrefix = "/users/$uid/%"
    try {
      db.schemaQueries.deleteEntitiesForUser(scopePrefix)
      db.schemaQueries.deleteSyncCursorsForUser(uid)
      log.i { "wipeDataForUser: cleared entity and sync_cursor rows for uid=$uid" }
    } catch (e: Exception) {
      log.e(e) { "wipeDataForUser failed for uid=$uid" }
    }
  }

  companion object {
    private const val TAG = "DatabaseIntegrityChecker"
  }
}

/** Cached result of the startup integrity check. Computed once per process launch. */
data class DatabaseHealth(val isCorrupted: Boolean)
