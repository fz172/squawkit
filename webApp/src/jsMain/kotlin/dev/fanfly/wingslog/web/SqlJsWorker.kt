package dev.fanfly.wingslog.web

import org.w3c.dom.Worker

/**
 * Builds the IndexedDB-persisting sql.js worker. The `new URL(..., import.meta.url)` form lets
 * webApp's webpack bundle the worker (and the sql.js it imports); the file lives in
 * `src/jsMain/resources/sqljs-idb.worker.js`.
 */
internal fun createSqlJsWorker(): Worker =
  js("new Worker(new URL('./sqljs-idb.worker.js', import.meta.url))").unsafeCast<Worker>()
