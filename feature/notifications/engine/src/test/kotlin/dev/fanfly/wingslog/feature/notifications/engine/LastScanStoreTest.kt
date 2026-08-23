package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class LastScanStoreTest {
  private companion object {
    const val UID = "user-lastscan-001"
    const val OTHER_UID = "user-lastscan-002"
    val AT = Instant.fromEpochMilliseconds(1_700_000_000_000)
  }

  private lateinit var db: WingsLogDatabase
  private lateinit var store: LastScanStore

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    store = LastScanStore(db)
  }

  @Test
  fun lastScanAt_neverRecorded_returnsNull() = runTest {
    assertThat(store.lastScanAt(UID)).isNull()
  }

  @Test
  fun record_thenRead_roundTripsTheInstant() = runTest {
    store.record(UID, AT)
    assertThat(store.lastScanAt(UID)).isEqualTo(AT)
  }

  @Test
  fun record_twice_keepsTheLatest() = runTest {
    val later = AT.plus(kotlin.time.Duration.parse("3h"))
    store.record(UID, AT)
    store.record(UID, later)
    assertThat(store.lastScanAt(UID)).isEqualTo(later)
  }

  /** Keyed by uid like the rest of `sync_config` — one account's scan must not debounce another's. */
  @Test
  fun record_isScopedToTheUid() = runTest {
    store.record(UID, AT)
    assertThat(store.lastScanAt(OTHER_UID)).isNull()
  }

  /** A hand-edited or corrupted value reads as "never scanned", which scans rather than staying silent. */
  @Test
  fun lastScanAt_unparseableValue_returnsNull() = runTest {
    db.schemaQueries.upsertConfig(UID, "urgency_last_scan_at", "not-a-number")
    assertThat(store.lastScanAt(UID)).isNull()
  }
}
