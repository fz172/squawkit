package dev.fanfly.wingslog.feature.notifications.engine

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
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
  fun record_thenRead_roundTripsEveryField() = runTest {
    val record = ScanRecord(
      at = AT,
      trigger = ScanTrigger.SESSION_BOUNDARY,
      recordsExamined = 42,
      crossingsFound = 7,
      crossingsSuppressed = 3,
      notificationsPosted = 2,
    )
    store.record(UID, record)
    assertThat(store.lastScan(UID)).isEqualTo(record)
    assertThat(store.lastScanAt(UID)).isEqualTo(AT)
  }

  @Test
  fun record_twice_keepsTheLatest() = runTest {
    val later = AT.plus(kotlin.time.Duration.parse("3h"))
    store.record(UID, scanAt(AT))
    store.record(UID, scanAt(later))
    assertThat(store.lastScanAt(UID)).isEqualTo(later)
  }

  @Test
  fun clear_makesItReadAsNeverScanned() = runTest {
    store.record(UID, scanAt(AT))
    store.clear(UID)
    assertThat(store.lastScan(UID)).isNull()
  }

  /** Keyed by uid like the rest of `sync_config` — one account's scan must not debounce another's. */
  @Test
  fun record_isScopedToTheUid() = runTest {
    store.record(UID, scanAt(AT))
    assertThat(store.lastScanAt(OTHER_UID)).isNull()
  }

  /** A hand-edited or corrupted value reads as "never scanned", which scans rather than staying silent. */
  @Test
  fun lastScanAt_unparseableValue_returnsNull() = runTest {
    db.schemaQueries.upsertConfig(UID, "urgency_last_scan", "not-a-record")
    assertThat(store.lastScan(UID)).isNull()
  }

  /** A line written by a future version decodes to null rather than to wrong numbers. */
  @Test
  fun lastScan_unknownVersion_returnsNull() = runTest {
    db.schemaQueries.upsertConfig(UID, "urgency_last_scan", "2|123|SCHEDULED|1|2|3|4")
    assertThat(store.lastScan(UID)).isNull()
  }

  private fun scanAt(at: Instant) = ScanRecord(
    at = at,
    trigger = ScanTrigger.SCHEDULED,
    recordsExamined = 0,
    crossingsFound = 0,
    crossingsSuppressed = 0,
    notificationsPosted = 0,
  )
}
