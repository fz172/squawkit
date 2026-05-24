// IndexedDB-backed sql.js worker for the SQLDelight WebWorkerDriver.
//
// Speaks the same message protocol as @cashapp/sqldelight-sqljs-worker (exec / *_transaction),
// but instead of a purely in-memory `new SQL.Database()` it restores the database from IndexedDB
// on startup and writes it back (debounced) after every mutating statement, so data survives
// page reloads. Persistence is whole-file: db.export() -> IndexedDB blob. Fine for our small DB.
import initSqlJs from "sql.js";

const IDB_NAME = "wingslog-sqljs";
const STORE = "db";
const KEY = "wingslog.db";
const SAVE_DEBOUNCE_MS = 150;

let db = null;
let idb = null;
let saveTimer = null;

function openIdb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function idbGet(key) {
  return new Promise((resolve, reject) => {
    const tx = idb.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).get(key);
    req.onsuccess = () => resolve(req.result || null);
    req.onerror = () => reject(req.error);
  });
}

function idbPut(key, value) {
  return new Promise((resolve, reject) => {
    const tx = idb.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(value, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

function scheduleSave() {
  if (saveTimer !== null) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      idbPut(KEY, db.export());
    } catch (e) {
      // best-effort persistence
    }
  }, SAVE_DEBOUNCE_MS);
}

async function createDatabase() {
  const SQL = await initSqlJs({ locateFile: () => "/sql-wasm.wasm" });
  idb = await openIdb();
  const saved = await idbGet(KEY);
  db = saved ? new SQL.Database(new Uint8Array(saved)) : new SQL.Database();
}

function onModuleReady() {
  const data = this.data;
  switch (data && data.action) {
    case "exec": {
      if (!data["sql"]) throw new Error("exec: Missing query string");
      const results = db.exec(data.sql, data.params)[0] ?? { values: [] };
      scheduleSave();
      return postMessage({ id: data.id, results });
    }
    case "begin_transaction":
      return postMessage({ id: data.id, results: db.exec("BEGIN TRANSACTION;") });
    case "end_transaction": {
      const results = db.exec("END TRANSACTION;");
      scheduleSave();
      return postMessage({ id: data.id, results });
    }
    case "rollback_transaction":
      return postMessage({ id: data.id, results: db.exec("ROLLBACK TRANSACTION;") });
    default:
      throw new Error(`Unsupported action: ${data && data.action}`);
  }
}

function onError(err) {
  return postMessage({ id: this.data.id, error: err });
}

if (typeof importScripts === "function") {
  const sqlModuleReady = createDatabase();
  self.onmessage = (event) =>
    sqlModuleReady.then(onModuleReady.bind(event)).catch(onError.bind(event));
}
