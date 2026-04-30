package dev.fanfly.wingslog.core.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

actual class DriverFactory(private val context: Context) {
  actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
    schema = WingsLogDatabase.Schema,
    context = context,
    name = WINGSLOG_DB_NAME,
  )
}
