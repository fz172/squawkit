package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val MEMBER = "member-jan-001"
private const val HOST = "host-jan-001"
private const val SHARED_AC = "ac-shared-jan"
private const val OWN_AC = "ac-own-jan"

@OptIn(ExperimentalCoroutinesApi::class)
class SharedScopeJanitorTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var janitor: SharedScopeJanitor

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous().create(driver)
    db = createWingsLogDatabase(driver)
    janitor = SharedScopeJanitor(db, DatabaseWriteLock())
  }

  /** A shared aircraft in the host's tree (doc + nested log + cursor) plus the member's own aircraft. */
  private suspend fun seedFixture() {
    seedEntity(CollectionKind.Aircraft, EntityScope.userRoot(HOST), SHARED_AC)
    seedEntity(CollectionKind.MaintenanceLog, EntityScope.aircraftChild(HOST, SHARED_AC), "log-1")
    db.schemaQueries.upsertCursor(
      MEMBER, CollectionKind.MaintenanceLog,
      EntityScope.aircraftChild(HOST, SHARED_AC).toPath(), false, null, 0, null,
    )
    seedEntity(CollectionKind.Aircraft, EntityScope.userRoot(MEMBER), OWN_AC)
  }

  @Test
  fun purges_a_shared_aircraft_with_no_live_ref() = runTest {
    seedFixture()
    janitor.purgeRevoked(MEMBER, liveShares = emptySet())

    assertThat(aircraftAt(EntityScope.userRoot(HOST))).isEmpty()
    assertThat(logsAt(EntityScope.aircraftChild(HOST, SHARED_AC))).isEmpty()
    assertThat(cursor(EntityScope.aircraftChild(HOST, SHARED_AC))).isNull()
    // Own aircraft untouched.
    assertThat(aircraftAt(EntityScope.userRoot(MEMBER))).hasSize(1)
  }

  @Test
  fun keeps_a_shared_aircraft_that_still_has_a_live_ref() = runTest {
    seedFixture()
    janitor.purgeRevoked(MEMBER, liveShares = setOf(HOST to SHARED_AC))

    assertThat(aircraftAt(EntityScope.userRoot(HOST))).hasSize(1)
    assertThat(logsAt(EntityScope.aircraftChild(HOST, SHARED_AC))).hasSize(1)
    assertThat(cursor(EntityScope.aircraftChild(HOST, SHARED_AC))).isNotNull()
  }

  private suspend fun seedEntity(kind: CollectionKind, scope: EntityScope, id: String) {
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope.toPath(),
      id = id,
      payload = byteArrayOf(0x01),
      payload_schema = kind.schemaName,
      updated_at = 1000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
    )
  }

  private fun aircraftAt(scope: EntityScope) =
    db.schemaQueries.selectAll(CollectionKind.Aircraft, scope.toPath()).executeAsList()

  private fun logsAt(scope: EntityScope) =
    db.schemaQueries.selectAll(CollectionKind.MaintenanceLog, scope.toPath()).executeAsList()

  private fun cursor(scope: EntityScope) =
    db.schemaQueries.selectCursor(MEMBER, CollectionKind.MaintenanceLog, scope.toPath())
      .executeAsOneOrNull()
}
