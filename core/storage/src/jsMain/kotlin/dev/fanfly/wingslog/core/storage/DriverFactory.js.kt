package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.Worker

/**
 * Web driver backed by sql.js running in a Web Worker. The [worker] is supplied by the host app
 * (webApp builds it from its bundled `sqljs-idb.worker.js`, which persists to IndexedDB) so the
 * worker file is resolved by the app's own webpack build.
 *
 * Unlike the Android/iOS sync drivers, the web-worker driver does not create the schema in its
 * constructor, and [createDriver] is synchronous — so schema creation is kicked off on a background
 * coroutine. The worker processes messages FIFO, so the CREATE statements are enqueued before any
 * later query.
 */
actual class DriverFactory(private val worker: Worker) {
  actual fun createDriver(): SqlDriver {
    val driver = WebWorkerDriver(worker)
    CoroutineScope(Dispatchers.Default).launch {
      try {
        WingsLogDatabase.Schema.create(driver).await()
      } catch (e: Throwable) {
        // The DB was restored from IndexedDB and the tables already exist — expected on reload.
        // TODO(web-sqlite-wasm): replace swallow with version-aware migrate (see M7 in the plan).
        Logger.withTag("DriverFactory-JS")
          .i { "Schema.create skipped (existing DB): ${e.message}" }
      }
    }
    return driver
  }
}
