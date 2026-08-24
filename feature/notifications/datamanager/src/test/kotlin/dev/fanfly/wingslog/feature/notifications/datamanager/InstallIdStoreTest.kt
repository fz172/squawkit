package dev.fanfly.wingslog.feature.notifications.datamanager

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class InstallIdStoreTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var store: InstallIdStore

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    store = InstallIdStore(db)
  }

  @Test
  fun getOrCreate_mintsAnIdOnFirstCall() = runTest {
    assertThat(store.getOrCreate()).isNotEmpty()
  }

  @Test
  fun getOrCreate_isStableAcrossCalls() = runTest {
    // The whole point: this id keys users/{uid}/push_devices/{installationId}, so a value that
    // changed per call would orphan a Firestore doc on every registration pass.
    assertThat(store.getOrCreate()).isEqualTo(store.getOrCreate())
  }

  @Test
  fun getOrCreate_isStableAcrossStoreInstances() = runTest {
    // Persisted, not held in memory — it has to survive a process restart, which is the case that
    // actually matters and the one an in-memory id would silently pass in a single-instance test.
    val first = store.getOrCreate()

    assertThat(InstallIdStore(db).getOrCreate()).isEqualTo(first)
  }

  @Test
  fun getOrCreate_concurrentCallersAgreeOnOneId() = runTest {
    // Two callers racing before either has written must not each mint their own — the loser's id
    // would be the one already written to Firestore, and it would never be cleaned up.
    val ids = (1..8).map { async { store.getOrCreate() } }.awaitAll()

    assertThat(ids.toSet()).hasSize(1)
  }

  @Test
  fun getOrCreate_survivesAUserScopedWipe() = runTest {
    // device_config is deliberately outside every wipe query (see its table comment): the id must
    // outlive sign-out and account switching on a shared device. This pins that it is not swept up
    // by the sign-out wipe the way sync_cursor and urgency_watermark are.
    val before = store.getOrCreate()

    db.schemaQueries.deleteEntitiesForUser("/users/any-uid/%")
    db.schemaQueries.deleteSyncCursorsForUser("any-uid")
    db.schemaQueries.deleteWatermarksForUser("any-uid")

    assertThat(store.getOrCreate()).isEqualTo(before)
  }
}
