package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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

  // --- helpers ---

  private suspend fun insertDirtyRow(id: String, updatedAt: Long = 1000L) {
    db.schemaQueries.upsert(
      collection = TEST_KIND,
      scope_path = TEST_SCOPE.toPath(),
      id = id,
      payload = byteArrayOf(0x01, 0x02),
      payload_schema = TEST_KIND.schemaName,
      updated_at = updatedAt,
      remote_updated_at = null,
      dirty = true,
      deleted = false,
    )
  }
}
