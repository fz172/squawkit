package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * Reads `INTEGER AS kotlin.Boolean` columns through [SqlCursor.getLong] instead of
 * [SqlCursor.getBoolean].
 *
 * **Why this exists.** `web-worker-driver-js` (2.3.2) does not convert booleans:
 *
 * ```kotlin
 * override fun getLong(index: Int): Long? = (values[currentRow][index] as? Double)?.toLong()   // converts
 * override fun getBoolean(index: Int): Boolean? = values[currentRow][index].unsafeCast<Boolean?>()  // does not
 * ```
 *
 * SQLite stores booleans as INTEGER, so `getBoolean` hands back the JS *number* `1` relabelled as a
 * `Boolean`. Kotlin/JS performs no runtime check on `Boolean`, so it lands in a `Boolean` field
 * intact and every `== true` / `!= true` comparison against it is false. `if (flag)` still works,
 * because that compiles to a JS truthiness test — which is exactly why the bug hid for so long:
 * only the strict comparisons broke, and only on web.
 *
 * The three that broke were `HydrationRunner`'s `local?.dirty != true` (unsynced local edits treated
 * as clean and overwritten), `SyncEngine`'s `?.hydrated != true` (every collection re-pulled from
 * Firestore on every page load), and `NotificationPrefsManagerImpl`'s `cursor?.hydrated == true`
 * (notification settings never resolving). Fixing the seam rather than those three call sites is
 * deliberate: the comparisons are correct Kotlin, and the fourth one written later would have had
 * the same bug.
 *
 * Applied to the JS driver only ([DriverFactory]); the Android and iOS drivers map `Boolean`
 * correctly and need no wrapper. It lives in `commonMain` so it can be unit-tested off a fake
 * cursor without a browser.
 *
 * Remove once the upstream driver converts `getBoolean` — a version bump alone will not break this,
 * since reading a boolean column via `getLong` stays correct either way.
 */
class BooleanNormalizingSqlDriver(
  private val delegate: SqlDriver,
) : SqlDriver by delegate {

  override fun <R> executeQuery(
    identifier: Int?,
    sql: String,
    mapper: (SqlCursor) -> QueryResult<R>,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<R> = delegate.executeQuery(
    identifier = identifier,
    sql = sql,
    mapper = { cursor -> mapper(BooleanNormalizingCursor(cursor)) },
    parameters = parameters,
    binders = binders,
  )
}

/**
 * Everything delegates untouched except [getBoolean], which re-reads the column as the integer it
 * actually is. `NULL` stays null; anything non-zero is `true`, matching SQLite's own truthiness and
 * the JDBC/native drivers' behaviour.
 */
internal class BooleanNormalizingCursor(
  private val delegate: SqlCursor,
) : SqlCursor {
  override fun next(): QueryResult<Boolean> = delegate.next()
  override fun getString(index: Int): String? = delegate.getString(index)
  override fun getLong(index: Int): Long? = delegate.getLong(index)
  override fun getBytes(index: Int): ByteArray? = delegate.getBytes(index)
  override fun getDouble(index: Int): Double? = delegate.getDouble(index)
  override fun getBoolean(index: Int): Boolean? = delegate.getLong(index)
    ?.let { it != 0L }
}
