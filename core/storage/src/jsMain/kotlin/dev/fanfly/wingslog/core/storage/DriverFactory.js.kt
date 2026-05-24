package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.Worker

/**
 * Web driver backed by sql.js running in a Web Worker (the `@cashapp/sqldelight-sqljs-worker`
 * prebuilt worker, which loads the sql.js WASM engine). Webpack bundles the worker via the
 * `new Worker(new URL(..., import.meta.url))` form.
 *
 * Unlike the Android/iOS sync drivers, the web-worker driver does not create the schema in its
 * constructor, and [createDriver] is synchronous — so schema creation is kicked off here and
 * awaited on a background coroutine. The worker processes messages FIFO, so the CREATE statements
 * are enqueued before any later query.
 */
actual class DriverFactory {
  actual fun createDriver(): SqlDriver {
    val driver = WebWorkerDriver(
      js("""new Worker(new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url))""")
        .unsafeCast<Worker>(),
    )
    CoroutineScope(Dispatchers.Default).launch {
      WingsLogDatabase.Schema.create(driver).await()
    }
    return driver
  }
}
