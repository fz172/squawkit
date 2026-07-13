package dev.fanfly.wingslog.core.storage.impl

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.WireCodec
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

private const val TEST_USER_ID = "user-test-001"
private const val TEST_AIRCRAFT_ID = "aircraft-test-001"
private const val OTHER_USER_ID = "user-other-999"

@OptIn(ExperimentalCoroutinesApi::class)
private const val CURRENT_UID = "uid-writer"

class SqlDelightEntityStoreTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var store: SqlDelightEntityStore<Aircraft>
  private lateinit var testClock: TestClock

  private val ioContext = UnconfinedTestDispatcher()
  private val codec = WireCodec(Aircraft.ADAPTER)
  private val scopeA = EntityScope.userRoot(TEST_USER_ID)
  private val scopeB = EntityScope.userRoot(OTHER_USER_ID)

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    // Schema is async-generated; the sync JVM driver wraps it via .synchronous().
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)

    testClock = TestClock(Instant.fromEpochMilliseconds(1_000_000L))
    store = SqlDelightEntityStore(
      kind = CollectionKind.Aircraft,
      codec = codec,
      db = db,
      ioContext = ioContext,
      clock = testClock,
      currentUid = { CURRENT_UID },
    )
  }

  // ---- authorship (design §7.5) ----

  @Test
  fun put_stamps_the_writing_account_as_author() = runTest(ioContext) {
    val aircraft = buildTestAircraft(id = TEST_AIRCRAFT_ID, tailNumber = "N12345")

    store.put(TEST_AIRCRAFT_ID, aircraft, scopeA)

    // Who wrote this revision is what §7.5 attests: it is read back to tell a technician signing
    // their own work apart from someone else attributing work to them.
    val row = store.observe(TEST_AIRCRAFT_ID, scopeA).first()
    assertThat(row?.writerUid).isEqualTo(CURRENT_UID)
  }

  @Test
  fun signedOut_writes_carry_no_author() = runTest(ioContext) {
    val anonymous = SqlDelightEntityStore(
      kind = CollectionKind.Aircraft,
      codec = codec,
      db = db,
      ioContext = ioContext,
      clock = testClock,
      currentUid = { null },
    )
    val aircraft = buildTestAircraft(id = TEST_AIRCRAFT_ID, tailNumber = "N12345")

    anonymous.put(TEST_AIRCRAFT_ID, aircraft, scopeA)

    // Null means "unknown", which the UI reports as neither signed nor assigned.
    assertThat(anonymous.observe(TEST_AIRCRAFT_ID, scopeA).first()?.writerUid).isNull()
  }

  // ---- put + observeAll ----

  @Test
  fun put_then_observeAll_emits_row() = runTest(ioContext) {
    val aircraft =
      buildTestAircraft(id = TEST_AIRCRAFT_ID, tailNumber = "N12345")

    store.put(TEST_AIRCRAFT_ID, aircraft, scopeA)

    val emissions: List<StorageEntity<Aircraft>> = store.observeAll(scopeA)
      .first()
    assertThat(emissions).hasSize(1)
    assertThat(emissions[0].id).isEqualTo(TEST_AIRCRAFT_ID)
    assertThat(emissions[0].value.tail_number).isEqualTo("N12345")
  }

  // ---- delete + observe + observeAll ----

  @Test
  fun delete_makes_observe_emit_null() = runTest(ioContext) {
    store.put(
      TEST_AIRCRAFT_ID,
      buildTestAircraft(id = TEST_AIRCRAFT_ID),
      scopeA
    )
    store.delete(TEST_AIRCRAFT_ID, scopeA)

    val result = store.observe(TEST_AIRCRAFT_ID, scopeA)
      .first()

    assertThat(result).isNull()
  }

  @Test
  fun delete_makes_observeAll_omit_deleted_row() = runTest(ioContext) {
    store.put(
      TEST_AIRCRAFT_ID,
      buildTestAircraft(id = TEST_AIRCRAFT_ID),
      scopeA
    )
    store.delete(TEST_AIRCRAFT_ID, scopeA)

    val emissions: List<StorageEntity<Aircraft>> = store.observeAll(scopeA)
      .first()

    assertThat(emissions).isEmpty()
  }

  // ---- scope isolation ----

  @Test
  fun scopes_are_isolated_put_in_A_not_visible_in_B() = runTest(ioContext) {
    store.put(
      TEST_AIRCRAFT_ID,
      buildTestAircraft(id = TEST_AIRCRAFT_ID),
      scopeA
    )

    val emissionsInB: List<StorageEntity<Aircraft>> = store.observeAll(scopeB)
      .first()

    assertThat(emissionsInB).isEmpty()
  }

  // ---- dirty flag on put ----

  @Test
  fun put_marks_row_as_dirty() = runTest(ioContext) {
    store.put(
      TEST_AIRCRAFT_ID,
      buildTestAircraft(id = TEST_AIRCRAFT_ID),
      scopeA
    )

    val dirtyRows = db.schemaQueries.selectDirty(limit = 10L)
      .awaitAsList()

    assertThat(dirtyRows).hasSize(1)
    assertThat(dirtyRows[0].id).isEqualTo(TEST_AIRCRAFT_ID)
    assertThat(dirtyRows[0].deleted).isFalse()
  }

  // ---- dirty flag + deleted flag on delete ----

  @Test
  fun delete_marks_row_dirty_and_deleted() = runTest(ioContext) {
    store.put(
      TEST_AIRCRAFT_ID,
      buildTestAircraft(id = TEST_AIRCRAFT_ID),
      scopeA
    )
    // Advance clock so the delete gets a later timestamp than the put.
    testClock.advanceBy(1_000L)
    store.delete(TEST_AIRCRAFT_ID, scopeA)

    val dirtyRows = db.schemaQueries.selectDirty(limit = 10L)
      .awaitAsList()

    // Only one row for this id (upsert replaces).
    val row = dirtyRows.single { it.id == TEST_AIRCRAFT_ID }
    assertThat(row.deleted).isTrue()
  }

  // ---- ordering ----

  @Test
  fun observeAll_orders_by_updatedAt_DESC() = runTest(ioContext) {
    val idOlder = "aircraft-older"
    val idNewer = "aircraft-newer"

    // Put older first at t=1_000_000.
    store.put(
      idOlder,
      buildTestAircraft(id = idOlder, tailNumber = "N00001"),
      scopeA
    )
    // Advance clock so the second put gets a strictly later timestamp.
    testClock.advanceBy(5_000L)
    store.put(
      idNewer,
      buildTestAircraft(id = idNewer, tailNumber = "N99999"),
      scopeA
    )

    val emissions: List<StorageEntity<Aircraft>> = store.observeAll(scopeA)
      .first()

    assertThat(emissions).hasSize(2)
    // Newest first — updatedAt DESC.
    assertThat(emissions[0].id).isEqualTo(idNewer)
    assertThat(emissions[1].id).isEqualTo(idOlder)
  }

  // ---- observe single id ----

  @Test
  fun put_then_observe_emits_the_entity() = runTest(ioContext) {
    val aircraft =
      buildTestAircraft(id = TEST_AIRCRAFT_ID, tailNumber = "N54321")

    store.put(TEST_AIRCRAFT_ID, aircraft, scopeA)

    val entity = store.observe(TEST_AIRCRAFT_ID, scopeA)
      .first()
    assertThat(entity).isNotNull()
    assertThat(entity!!.value.tail_number).isEqualTo("N54321")
  }

  @Test
  fun observe_nonexistent_id_emits_null() = runTest(ioContext) {
    val entity = store.observe("does-not-exist", scopeA)
      .first()
    assertThat(entity).isNull()
  }

  // ---- helpers ----

  private fun buildTestAircraft(
    id: String = TEST_AIRCRAFT_ID,
    tailNumber: String = "N00000",
    make: String = "Cessna",
    model: String = "172",
  ): Aircraft = Aircraft(
    id = id,
    tail_number = tailNumber,
    make = make,
    model = model,
  )
}

/**
 * Deterministic [Clock] for tests. Starts at [initial] and advances only when [advanceBy] is
 * called explicitly.
 */
class TestClock(initial: Instant) : Clock {
  private var currentMs: Long = initial.toEpochMilliseconds()

  override fun now(): Instant = Instant.fromEpochMilliseconds(currentMs)

  fun advanceBy(ms: Long) {
    currentMs += ms
  }
}
