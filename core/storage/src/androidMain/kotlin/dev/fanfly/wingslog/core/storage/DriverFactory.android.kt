package dev.fanfly.wingslog.core.storage

import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

actual class DriverFactory(private val context: Context) {
  // The schema is async-generated (generateAsync=true for the web driver); the synchronous
  // Android driver wraps it via .synchronous().
  actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
    schema = WingsLogDatabase.Schema.synchronous(),
    context = context,
    name = WINGSLOG_DB_NAME,
  )
}
