package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.Worker

/**
 * Web driver backed by the OPFS sqlite-wasm worker supplied by the host app (webApp builds it from
 * its bundled `sqlite-wasm-opfs.worker.js`, which persists to OPFS), so the worker file is resolved
 * by the app's own webpack build.
 *
 * Unlike the Android/iOS drivers, [createDriver] is synchronous and the worker driver does not
 * create the schema in its constructor — so schema setup runs on a background coroutine. The worker
 * processes messages FIFO, so the statements enqueued here run before any later app query.
 */
actual class DriverFactory(private val worker: Worker) {
  actual fun createDriver(): SqlDriver {
    val driver = WebWorkerDriver(worker)
    CoroutineScope(Dispatchers.Default).launch { ensureSchema(driver) }
    return driver
  }

  /**
   * Version-aware create/migrate, replacing the earlier "create and swallow if it already exists"
   * hack. We enqueue `Schema.create` *first* (no awaited round-trip before it) so the tables exist
   * ahead of any app query on a fresh DB. On an existing DB the first `CREATE TABLE` throws
   * "already exists"; we then read `PRAGMA user_version` and run [SqlSchema.migrate] if the on-disk
   * version is behind. `user_version` lives in the SQLite file header, so it persists in OPFS.
   */
  private suspend fun ensureSchema(driver: SqlDriver) {
    val schema = WingsLogDatabase.Schema
    val target = schema.version
    val created = try {
      schema.create(driver).await()
      true
    } catch (e: Throwable) {
      // Expected on every launch after the first: the tables already exist.
      log.i { "Schema.create skipped (existing DB): ${e.message}" }
      false
    }
    if (created) {
      driver.setUserVersion(target)
      return
    }
    val current = driver.userVersion()
    if (current in 1 until target) {
      log.i { "migrating local DB schema $current -> $target" }
      schema.migrate(driver, current, target).await()
      driver.setUserVersion(target)
    }
  }

  private suspend fun SqlDriver.userVersion(): Long =
    executeQuery(
      identifier = null,
      sql = "PRAGMA user_version",
      mapper = { cursor ->
        QueryResult.AsyncValue { if (cursor.next().await()) cursor.getLong(0) ?: 0L else 0L }
      },
      parameters = 0,
    ).await()

  private suspend fun SqlDriver.setUserVersion(version: Long) {
    execute(identifier = null, sql = "PRAGMA user_version = $version", parameters = 0).await()
  }

  private companion object {
    private val log = Logger.withTag("DriverFactory-JS")
  }
}
