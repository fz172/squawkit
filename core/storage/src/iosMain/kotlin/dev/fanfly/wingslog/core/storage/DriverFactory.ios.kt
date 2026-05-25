package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

actual class DriverFactory {
  // generateAsync=true makes the schema async; the synchronous Native driver wraps it.
  actual fun createDriver(): SqlDriver = NativeSqliteDriver(
    schema = WingsLogDatabase.Schema.synchronous(),
    name = WINGSLOG_DB_NAME,
  )
}
