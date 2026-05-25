package dev.fanfly.wingslog.web

import org.w3c.dom.Worker

/**
 * Builds the OPFS-backed sqlite-wasm worker for SQLDelight's WebWorkerDriver. The
 * `new URL(..., import.meta.url)` form lets webApp's webpack bundle the worker (and the
 * `@sqlite.org/sqlite-wasm` it imports); the file lives in
 * `src/jsMain/resources/sqlite-wasm-opfs.worker.js`.
 */
internal fun createSqliteWorker(): Worker =
  js("new Worker(new URL('./sqlite-wasm-opfs.worker.js', import.meta.url))").unsafeCast<Worker>()
