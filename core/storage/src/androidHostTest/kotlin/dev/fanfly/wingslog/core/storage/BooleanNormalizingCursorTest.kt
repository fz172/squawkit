package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the web boolean mapping (see [BooleanNormalizingSqlDriver]).
 *
 * [FakeJsCursor] reproduces what `web-worker-driver-js` actually does: `getLong` converts, while
 * `getBoolean` hands back the raw integer relabelled as a `Boolean`. That mis-typing cannot be
 * expressed in Kotlin/JVM, so the fake returns the *wrong boolean* instead — `1` reading as `false`
 * is the same observable failure the strict `== true` comparisons hit on JS, and it fails these
 * tests if the wrapper is removed.
 */
class BooleanNormalizingCursorTest {

  private class FakeJsCursor(private val row: List<Any?>) : SqlCursor {
    override fun next(): QueryResult<Boolean> = QueryResult.Value(true)
    override fun getString(index: Int): String? = row[index] as? String
    override fun getLong(index: Int): Long? = (row[index] as? Number)?.toLong()
    override fun getBytes(index: Int): ByteArray? = row[index] as? ByteArray
    override fun getDouble(index: Int): Double? =
      (row[index] as? Number)?.toDouble()

    /** What the JS driver effectively does: no conversion from the stored integer. */
    override fun getBoolean(index: Int): Boolean? = row[index] as? Boolean
  }

  private fun cursorOver(vararg values: Any?) =
    BooleanNormalizingCursor(FakeJsCursor(values.toList()))

  @Test
  fun storedOne_readsAsTrue() {
    assertThat(cursorOver(1L).getBoolean(0)).isTrue()
  }

  @Test
  fun storedZero_readsAsFalse() {
    assertThat(cursorOver(0L).getBoolean(0)).isFalse()
  }

  /** The actual failure: `hydrated = 1` must satisfy `== true`, which is what broke on web. */
  @Test
  fun storedOne_satisfiesStrictEqualsTrue() {
    val hydrated: Boolean? = cursorOver(1L).getBoolean(0)
    assertThat(hydrated == true).isTrue()
    assertThat(hydrated != true).isFalse()
  }

  @Test
  fun nullStaysNull() {
    assertThat(cursorOver(null).getBoolean(0)).isNull()
  }

  /** SQLite's own truthiness — anything non-zero is true, not just 1. */
  @Test
  fun anyNonZero_readsAsTrue() {
    assertThat(cursorOver(2L).getBoolean(0)).isTrue()
    assertThat(cursorOver(-1L).getBoolean(0)).isTrue()
  }

  @Test
  fun theUnwrappedCursorIsWhatFails() {
    // Documents the bug this wrapper exists for: without it, a stored 1 does not read as true.
    assertThat(FakeJsCursor(listOf(1L)).getBoolean(0)).isNull()
  }

  @Test
  fun otherColumnTypesPassThroughUntouched() {
    val cursor = cursorOver("tail", 42L, 1.5, null)
    assertThat(cursor.getString(0)).isEqualTo("tail")
    assertThat(cursor.getLong(1)).isEqualTo(42L)
    assertThat(cursor.getDouble(2)).isEqualTo(1.5)
    assertThat(cursor.getBytes(3)).isNull()
  }
}
