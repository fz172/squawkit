// Durable SQLite storage for SQLDelight's WebWorkerDriver, backed by the official
// @sqlite.org/sqlite-wasm build on OPFS via its SyncAccessHandle-Pool (SAH-Pool) VFS.
//
// Replaces the interim sql.js -> IndexedDB worker (M3), which snapshotted the *entire* DB to
// IndexedDB after every write (O(db size), doesn't scale). This worker persists to real OPFS
// files (block I/O, scales). The SAH-Pool VFS needs no COOP/COEP headers, so it doesn't break
// Firebase signInWithPopup (the app's Google sign-in).
//
// It speaks the same message protocol as the worker it replaces, so the Kotlin side
// (WebWorkerDriver) is unchanged:
//   request : { id, action: "exec" | "begin_transaction" | "end_transaction" | "rollback_transaction", sql, params }
//   response: { id, results: { values: [[col, ...], ...] } }  — or { id, error }
// `values` rows are arrays of column values; INTEGER -> number, TEXT -> string, BLOB -> Uint8Array,
// NULL -> null. That matches what WorkerSqlCursor reads (results.values[row][col]).
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

const DB_PATH = "/wingslog.sqlite3";

let db = null;

async function createDatabase() {
  const sqlite3 = await sqlite3InitModule({
    // Emscripten fetches the wasm at runtime; webApp's webpack copies it to the bundle root
    // (webpack.config.d/sqlite-wasm-copy.js), mirroring the old sql.js wasm handling.
    locateFile: (file) => (file.endsWith(".wasm") ? "/sqlite3.wasm" : file),
  });
  const poolUtil = await sqlite3.installOpfsSAHPoolVfs({ name: "wingslog-opfs" });
  db = new poolUtil.OpfsSAHPoolDb(DB_PATH);
}

// rowMode "array" + returnValue "resultRows" returns rows as arrays of column values — the exact
// shape WebWorkerDriver expects. A mutation/PRAGMA returns [] (no rows), which the driver reads as
// rowCount 0, matching the previous sql.js worker's behavior.
function exec(sql, params) {
  return db.exec({
    sql,
    bind: params && params.length ? params : undefined,
    rowMode: "array",
    returnValue: "resultRows",
  });
}

function onMessage(data) {
  switch (data && data.action) {
    case "exec": {
      if (!data.sql) throw new Error("exec: Missing query string");
      return postMessage({ id: data.id, results: { values: exec(data.sql, data.params) } });
    }
    case "begin_transaction":
      return postMessage({ id: data.id, results: { values: exec("BEGIN TRANSACTION;") } });
    case "end_transaction":
      return postMessage({ id: data.id, results: { values: exec("END TRANSACTION;") } });
    case "rollback_transaction":
      return postMessage({ id: data.id, results: { values: exec("ROLLBACK TRANSACTION;") } });
    default:
      throw new Error(`Unsupported action: ${data && data.action}`);
  }
}

const ready = createDatabase();
self.onmessage = (event) =>
  ready
    .then(() => onMessage(event.data))
    .catch((error) =>
      postMessage({ id: event.data && event.data.id, error: String(error && error.message || error) }),
    );
