package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.SqlDriver
import dev.fanfly.wingslog.core.storage.blob.remoteStateAdapter
import dev.fanfly.wingslog.core.storage.db.Blob_object
import dev.fanfly.wingslog.core.storage.db.Entity
import dev.fanfly.wingslog.core.storage.db.Sync_cursor
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Builds a [WingsLogDatabase] with every [app.cash.sqldelight.ColumnAdapter] pre-wired. This is the
 * only supported way to construct the database — direct calls to `WingsLogDatabase(driver, …)`
 * would have to plumb the adapters manually and are easy to get wrong.
 */
fun createWingsLogDatabase(driver: SqlDriver): WingsLogDatabase = WingsLogDatabase(
  driver = driver,
  entityAdapter = Entity.Adapter(collectionAdapter = collectionKindAdapter),
  sync_cursorAdapter = Sync_cursor.Adapter(collectionAdapter = collectionKindAdapter),
  blob_objectAdapter = Blob_object.Adapter(remote_stateAdapter = remoteStateAdapter),
)
