package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

actual class DriverFactory {
  actual fun createDriver(): SqlDriver = NativeSqliteDriver(
    schema = WingsLogDatabase.Schema,
    name = WINGSLOG_DB_NAME,
  )
}
