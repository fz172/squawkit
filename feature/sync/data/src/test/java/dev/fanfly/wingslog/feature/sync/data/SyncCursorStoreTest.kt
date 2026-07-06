package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test


class SyncCursorStoreTest {
  companion object {
    private const val TEST_UID = "user-cursor-001"
    private const val TEST_AIRCRAFT_ID = "aircraft-cursor-001"
    private val TEST_SCOPE = EntityScope.aircraftChild(
      TEST_UID,
      TEST_AIRCRAFT_ID
    )
    private val TEST_KIND = CollectionKind.MaintenanceLog
  }

  private lateinit var db: WingsLogDatabase
  private lateinit var store: SyncCursorStore

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous().create(driver)
    db = createWingsLogDatabase(driver)
    store = SyncCursorStore(db)
  }

  // get — returns null when no cursor row exists

  @Test
  fun get_noCursorRow_returnsNull() = runTest {
    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor).isNull()
  }

  // markHydrated — writes cursor with hydrated=true, failed_attempts=0

  @Test
  fun markHydrated_createsCursorWithHydratedTrue() = runTest {
    store.markHydrated(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      lastSeenRemote = 5000L
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor).isNotNull()
    assertThat(cursor!!.hydrated).isTrue()
    assertThat(cursor.failedAttempts).isEqualTo(0)
    assertThat(cursor.lastSeenRemote).isEqualTo(5000L)
  }

  @Test
  fun markHydrated_withNullLastSeen_storesNullLastSeenRemote() = runTest {
    store.markHydrated(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      lastSeenRemote = null
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.lastSeenRemote).isNull()
    assertThat(cursor.hydrated).isTrue()
  }

  // recordFailure — increments failed_attempts, sets last_attempt_at, hydrated stays false

  @Test
  fun recordFailure_firstAttempt_failedAttemptsIsOne() = runTest {
    store.recordFailure(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor).isNotNull()
    assertThat(cursor!!.failedAttempts).isEqualTo(1)
    assertThat(cursor.hydrated).isFalse()
    assertThat(cursor.lastAttemptAt).isNotNull()
  }

  @Test
  fun recordFailure_calledTwice_failedAttemptsIsTwo() = runTest {
    store.recordFailure(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    store.recordFailure(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.failedAttempts).isEqualTo(2)
  }

  @Test
  fun recordFailure_afterHydrated_hydratedStaysTrue() = runTest {
    store.markHydrated(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      lastSeenRemote = 1000L
    )
    store.recordFailure(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    // hydrated stays true even after failure — subsequent markHydrated resets it
    assertThat(cursor!!.hydrated).isTrue()
    assertThat(cursor.failedAttempts).isEqualTo(1)
  }

  // advanceLastSeen — only advances when new remoteTs is strictly greater

  @Test
  fun advanceLastSeen_noCursorRow_createsCursorWithGivenTs() = runTest {
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 3000L
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor).isNotNull()
    assertThat(cursor!!.lastSeenRemote).isEqualTo(3000L)
  }

  @Test
  fun advanceLastSeen_newerTs_updatesLastSeenRemote() = runTest {
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 1000L
    )
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 5000L
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.lastSeenRemote).isEqualTo(5000L)
  }

  @Test
  fun advanceLastSeen_olderTs_doesNotDecrement() = runTest {
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 5000L
    )
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 1000L
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.lastSeenRemote).isEqualTo(5000L)
  }

  @Test
  fun advanceLastSeen_sameTs_doesNotDecrement() = runTest {
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 5000L
    )
    store.advanceLastSeen(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      remoteTs = 5000L
    )

    val cursor = store.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.lastSeenRemote).isEqualTo(5000L)
  }

  // Scope and kind isolation

  @Test
  fun cursors_differentScopesSameKind_areIsolated() = runTest {
    val scopeOther = EntityScope.aircraftChild(
      TEST_UID,
      "other-aircraft"
    )
    store.markHydrated(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      lastSeenRemote = 1000L
    )

    val cursorOther = store.get(
      TEST_UID,
      TEST_KIND,
      scopeOther
    )
    assertThat(cursorOther).isNull()
  }

  @Test
  fun cursors_differentKindsSameScope_areIsolated() = runTest {
    store.markHydrated(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE,
      lastSeenRemote = 1000L
    )

    val cursorOther = store.get(
      TEST_UID,
      CollectionKind.Aircraft,
      TEST_SCOPE
    )
    assertThat(cursorOther).isNull()
  }
}
