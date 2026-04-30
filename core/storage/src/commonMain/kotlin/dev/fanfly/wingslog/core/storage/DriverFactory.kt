package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific factory for the SQLDelight driver backing [WingsLogDatabaseFactory].
 *
 * Android: [app.cash.sqldelight.driver.android.AndroidSqliteDriver] over `wingslog.db`.
 * iOS:     [app.cash.sqldelight.driver.native.NativeSqliteDriver]     over `wingslog.db`.
 *
 * Tests use an in-memory driver via the SQLDelight JVM driver — see commonTest helpers in C6.
 */
expect class DriverFactory {
  fun createDriver(): SqlDriver
}

/** Convenience constant for the on-disk database file name. */
const val WINGSLOG_DB_NAME: String = "wingslog.db"
