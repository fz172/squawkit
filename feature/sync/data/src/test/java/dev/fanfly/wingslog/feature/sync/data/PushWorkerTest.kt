package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityCodecRegistry
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.WireCodec
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_USER_ID = "user-push-001"
private const val TEST_AIRCRAFT_ID = "aircraft-push-001"
private val TEST_SCOPE =
  EntityScope.aircraftChild(TEST_USER_ID, TEST_AIRCRAFT_ID)
private val TEST_KIND = CollectionKind.MaintenanceLog

private const val HOST_UID = "host-push-001"
private const val SHARED_AC = "aircraft-shared-001"
private val SHARED_SCOPE = EntityScope.aircraftChild(HOST_UID, SHARED_AC)

@OptIn(ExperimentalCoroutinesApi::class)
class PushWorkerTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var writer: SyncWriter
  private lateinit var worker: PushWorker

  private val ioContext = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)

    writer = mockk()
    worker = PushWorker(db = db, writer = writer, ioContext = ioContext)
  }

  // N dirty rows → writer.push() called for each row → all rows cleared (dirty=0)

  @Test
  fun run_nDirtyRows_pushCalledForEachAndDirtyCleared() = runTest(ioContext) {
    val ids = listOf("log-1", "log-2", "log-3")
    ids.forEach { id ->
      insertDirtyRow(id)
    }
    coEvery { writer.push(any()) } returns Unit

    val job = launch { worker.run(TEST_USER_ID) }
    // Let UnconfinedTestDispatcher run all pending coroutines synchronously.
    testScheduler.advanceUntilIdle()
    job.cancel()

    coVerify(exactly = ids.size) { writer.push(any()) }
    val remaining = db.schemaQueries.selectDirty(limit = 100L)
      .executeAsList()
    assertThat(remaining).isEmpty()
  }

  @Test
  fun run_drainsSharedAircraftScopeRows_whenARefExists() = runTest(ioContext) {
    // A store factory so the worker can observe the refs store and add the shared prefix.
    val codecs = EntityCodecRegistry().apply {
      register(CollectionKind.SharedAircraftRef, WireCodec(SharedAircraftRef.ADAPTER))
    }
    val storeFactory = EntityStoreFactory(
      db = db,
      codecs = codecs,
      ioContext = ioContext,
      writeLock = DatabaseWriteLock(),
    )
    // A live share pointing at another account's aircraft.
    storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
      .put(
        SHARED_AC,
        SharedAircraftRef(aircraft_id = SHARED_AC, host_uid = HOST_UID),
        EntityScope.userRoot(TEST_USER_ID),
      )
    // A dirty row under the shared aircraft's scope (the host's tree).
    insertDirtyRow("shared-log-1", scope = SHARED_SCOPE)

    val captured = slot<SyncWrite>()
    coEvery { writer.push(capture(captured)) } returns Unit
    val sharedWorker = PushWorker(
      db = db,
      writer = writer,
      ioContext = ioContext,
      storeFactory = storeFactory,
    )

    val job = launch { sharedWorker.run(TEST_USER_ID) }
    testScheduler.advanceUntilIdle()
    job.cancel()

    assertThat(captured.captured.scope).isEqualTo(SHARED_SCOPE)
    val remaining = db.schemaQueries.selectDirty(limit = 100L).executeAsList()
    assertThat(remaining).isEmpty()
  }

  @Test
  fun run_stampsPushingUidAsWriterUid() = runTest(ioContext) {
    insertDirtyRow("log-1")
    val captured = slot<SyncWrite>()
    coEvery { writer.push(capture(captured)) } returns Unit

    val job = launch { worker.run(TEST_USER_ID) }
    testScheduler.advanceUntilIdle()
    job.cancel()

    assertThat(captured.captured.writerUid).isEqualTo(TEST_USER_ID)
  }

  @Test
  fun run_noDirtyRows_writerNeverCalled() = runTest(ioContext) {
    coEvery { writer.push(any()) } returns Unit

    val job = launch { worker.run(TEST_USER_ID) }
    testScheduler.advanceUntilIdle()
    job.cancel()

    coVerify(exactly = 0) { writer.push(any()) }
  }

  // Batch commit throws → row stays dirty=1, drain stops after the failing row

  @Test
  fun run_writerThrows_rowRemainsDirty() = runTest(ioContext) {
    insertDirtyRow("log-fail")
    coEvery { writer.push(any()) } throws RuntimeException("write rejected")

    val job = launch { worker.run(TEST_USER_ID) }
    testScheduler.advanceUntilIdle()
    job.cancel()

    val remaining = db.schemaQueries.selectDirty(limit = 100L)
      .executeAsList()
    assertThat(remaining).hasSize(1)
    assertThat(remaining[0].id).isEqualTo("log-fail")
  }

  @Test
  fun run_writerThrowsOnFirstRow_subsequentRowsNotPushed() =
    runTest(ioContext) {
      // Insert two rows — the first fails; drain should stop, leaving both dirty.
      insertDirtyRow("log-a", updatedAt = 1000L)
      insertDirtyRow("log-b", updatedAt = 2000L)
      coEvery { writer.push(any()) } throws RuntimeException("first push fails")

      val job = launch { worker.run(TEST_USER_ID) }
      testScheduler.advanceUntilIdle()
      job.cancel()

      // Both rows still dirty because drain stopped after first failure.
      val remaining = db.schemaQueries.selectDirty(limit = 100L)
        .executeAsList()
      assertThat(remaining).hasSize(2)
    }

  @Test
  fun run_writerSucceedsOnFirstRowFailsOnSecond_firstRowClearedSecondRemainsDirty() =
    runTest(ioContext) {
      insertDirtyRow("log-ok", updatedAt = 1000L)
      insertDirtyRow("log-fail", updatedAt = 2000L)
      // First call succeeds, second throws.
      coEvery { writer.push(any()) }
        .returnsMany(Unit) andThenThrows RuntimeException("second push fails")

      val job = launch { worker.run(TEST_USER_ID) }
      testScheduler.advanceUntilIdle()
      job.cancel()

      val remaining = db.schemaQueries.selectDirty(limit = 100L)
        .executeAsList()
      assertThat(remaining).hasSize(1)
      assertThat(remaining[0].id).isEqualTo("log-fail")
    }

  // --- Revocation race: a denied push into a host's tree (docs/sharing §5.4) ---

  @Test
  fun run_permissionDeniedOnSharedScope_reconcilesAsRevokedAndShowsNoBanner() =
    runTest(ioContext) {
      // The member edited a shared aircraft offline, was revoked meanwhile, and the push lands
      // before the ref tombstone does. That is a revocation, not an expired session.
      val storeFactory = liveShareStoreFactory()
      insertDirtyRow("shared-log-1", scope = SHARED_SCOPE)
      // Only the host's subtree denies us — writes to our own tree (including the ref row the
      // factory just wrote) still succeed, exactly as the rules behave after a revoke.
      coEvery { writer.push(match { it.scope != SHARED_SCOPE }) } returns Unit
      coEvery { writer.push(match { it.scope == SHARED_SCOPE }) } throws permissionDenied()

      val failures = mutableListOf<SyncFailure?>()
      val revoked = mutableListOf<Pair<String, String>>()
      val telemetry = RecordingTelemetry()
      val sharedWorker = PushWorker(
        db = db,
        writer = writer,
        ioContext = ioContext,
        storeFactory = storeFactory,
        telemetry = telemetry,
      ).apply {
        failureSink = { failures += it }
        sharedScopeRevokedSink = { host, ac -> revoked += host to ac }
      }

      val job = launch { sharedWorker.run(TEST_USER_ID) }
      testScheduler.advanceUntilIdle()
      job.cancel()

      assertThat(revoked).containsExactly(HOST_UID to SHARED_AC)
      // No auth banner: nothing is wrong with the session, the share simply ended. (Nulls are the
      // successful own-tree pushes clearing the banner, which is not a failure.)
      assertThat(failures.filterNotNull()).isEmpty()
      assertThat(telemetry.deniedWrites).containsExactly(true)
      // The rows stay dirty here — the janitor hard-deletes the scope once the ref goes away, which
      // is what stops us retrying a write we will never be allowed to make.
    }

  @Test
  fun run_permissionDeniedOnOwnScope_stillRaisesAuthBanner() = runTest(ioContext) {
    // Same denial in the member's *own* tree means an expired token or a rules regression. It must
    // keep surfacing — swallowing it would hide a real breakage.
    insertDirtyRow("own-log-1", scope = TEST_SCOPE)
    coEvery { writer.push(any()) } throws permissionDenied()

    val failures = mutableListOf<SyncFailure?>()
    val revoked = mutableListOf<Pair<String, String>>()
    val telemetry = RecordingTelemetry()
    val ownWorker = PushWorker(
      db = db,
      writer = writer,
      ioContext = ioContext,
      telemetry = telemetry,
    ).apply {
      failureSink = { failures += it }
      sharedScopeRevokedSink = { host, ac -> revoked += host to ac }
    }

    val job = launch { ownWorker.run(TEST_USER_ID) }
    testScheduler.advanceUntilIdle()
    job.cancel()

    assertThat(revoked).isEmpty()
    assertThat(failures.filterNotNull()).hasSize(1)
    assertThat(failures.filterNotNull().first()).isInstanceOf(SyncFailure.AuthExpired::class.java)
    assertThat(telemetry.deniedWrites).containsExactly(false)
  }

  // --- helpers ---

  /** A store factory holding one live ref, so the worker drains the host's subtree too. */
  private suspend fun liveShareStoreFactory(): EntityStoreFactory {
    val codecs = EntityCodecRegistry().apply {
      register(CollectionKind.SharedAircraftRef, WireCodec(SharedAircraftRef.ADAPTER))
    }
    val storeFactory = EntityStoreFactory(
      db = db,
      codecs = codecs,
      ioContext = ioContext,
      writeLock = DatabaseWriteLock(),
    )
    storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
      .put(
        SHARED_AC,
        SharedAircraftRef(aircraft_id = SHARED_AC, host_uid = HOST_UID),
        EntityScope.userRoot(TEST_USER_ID),
      )
    return storeFactory
  }

  private fun permissionDenied(): FirebaseFirestoreException =
    mockk<FirebaseFirestoreException>(relaxed = true).also {
      every { it.code } returns FirestoreExceptionCode.PERMISSION_DENIED
    }

  /** Records what the worker reported, so the safety-valve counters can be asserted (PRD §9). */
  private class RecordingTelemetry : SyncTelemetry {
    val deniedWrites = mutableListOf<Boolean>()
    override fun permissionDeniedWrite(sharedScope: Boolean) {
      deniedWrites += sharedScope
    }

    override fun sharedScopeReconciled(trigger: String) = Unit
  }

  private suspend fun insertDirtyRow(
    id: String,
    updatedAt: Long = 1000L,
    scope: EntityScope = TEST_SCOPE,
  ) {
    db.schemaQueries.upsert(
      collection = TEST_KIND,
      scope_path = scope.toPath(),
      id = id,
      payload = byteArrayOf(0x01, 0x02),
      payload_schema = TEST_KIND.schemaName,
      updated_at = updatedAt,
      remote_updated_at = null,
      dirty = true,
      deleted = false,
      writer_uid = null,
    )
  }
}
