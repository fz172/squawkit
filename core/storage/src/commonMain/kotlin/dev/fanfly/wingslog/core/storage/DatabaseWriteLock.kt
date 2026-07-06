package dev.fanfly.wingslog.core.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes every local-database **write-unit** — a standalone mutating statement (`upsert`,
 * `clearDirty`, …) or a multi-statement [app.cash.sqldelight.Transacter] transaction — so that no
 * two are ever open at the same time. One shared instance is injected into every class that writes
 * the database (see `storageModule`).
 *
 * ### Why this exists
 * SQLDelight routes a write's change-notification to whatever transaction is "current" on the
 * driver ([app.cash.sqldelight.Transacter.notifyQueries] reads `driver.currentTransaction()`). On
 * JVM/Native that current transaction is **thread-confined** (`AndroidSqliteDriver` keeps it in a
 * `ThreadLocal`; the native driver aligns it to a thread), so a write on one thread never observes
 * an unrelated transaction on another.
 *
 * The web `WebWorkerDriver`, by contrast, tracks the current transaction in a single shared field.
 * On JS everything runs on one thread, and the sync engine keeps **suspending** transactions open
 * across `await()` points (`HydrationRunner`, `PullListener`). So a concurrent UI `EntityStore.put`
 * sees the sync engine's transaction as "current" and its notification gets stashed in that
 * transaction's pending set instead of firing — delivered late on commit, or **dropped entirely on
 * rollback** (`postTransactionCleanup` clears the pending tables without notifying). The dashboard
 * then never refreshes until a full page reload re-runs the query. Two concurrent transactions also
 * corrupt each other because the second's `newTransaction()` treats the first as its enclosing
 * parent.
 *
 * Holding this lock around every write-unit guarantees mutual exclusion: standalone writes always
 * see `currentTransaction() == null` (immediate notify) and transactions never nest-corrupt. It is
 * essentially free on Android/iOS (writes aren't a hot path and the driver already serializes them)
 * and essential on web.
 */
class DatabaseWriteLock {
  private val mutex = Mutex()

  /** Runs [block] with exclusive access to the database for the duration of the write-unit. */
  suspend fun <T> withLock(block: suspend () -> T): T =
    mutex.withLock { block() }
}
