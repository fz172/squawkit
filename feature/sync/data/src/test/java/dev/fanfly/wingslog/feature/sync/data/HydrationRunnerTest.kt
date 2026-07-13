package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test


class HydrationRunnerTest {
  private val TEST_UID = "user-hydration-001"
  private val TEST_AIRCRAFT_ID = "aircraft-hydration-001"
  private val TEST_SCOPE = EntityScope.aircraftChild(
    TEST_UID,
    TEST_AIRCRAFT_ID
  )
  private val TEST_KIND = CollectionKind.MaintenanceLog

  private lateinit var db: WingsLogDatabase
  private lateinit var fetcher: RemoteFetcher
  private lateinit var cursors: SyncCursorStore
  private lateinit var runner: HydrationRunner

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)

    fetcher = mockk()
    cursors = SyncCursorStore(db)
    runner = HydrationRunner(
      db = db,
      fetcher = fetcher,
      cursors = cursors
    )
  }

  // Success: N docs from RemoteFetcher → local has N rows with dirty=0, cursor hydrated=true,
  //          failed_attempts=0.

  @Test
  fun runFor_success_insertsAllDocsWithDirtyFalse() = runTest {
    val docs = listOf(
      buildRemoteEntity(
        id = "log-1",
        remoteTsMs = 1000L
      ),
      buildRemoteEntity(
        id = "log-2",
        remoteTsMs = 2000L
      ),
      buildRemoteEntity(
        id = "log-3",
        remoteTsMs = 3000L
      ),
    )
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } returns docs

    val result = runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    assertThat(result).isTrue()
    for (doc in docs) {
      val row = db.schemaQueries.selectOneForSync(
        TEST_KIND,
        TEST_SCOPE.toPath(),
        doc.id
      )
        .executeAsOneOrNull()
      assertThat(row).isNotNull()
      assertThat(row!!.dirty).isFalse()
      assertThat(row.remote_updated_at).isEqualTo(doc.remoteTsMs)
    }
  }

  @Test
  fun runFor_success_doesNotOverwriteDirtyLocalRows() = runTest {
    val localPayload = byteArrayOf(0x11)
    val remotePayload = byteArrayOf(0x22)
    db.schemaQueries.upsert(
      collection = TEST_KIND,
      scope_path = TEST_SCOPE.toPath(),
      id = "log-dirty",
      payload = localPayload,
      payload_schema = TEST_KIND.schemaName,
      updated_at = 500L,
      remote_updated_at = null,
      dirty = true,
      deleted = false,
      writer_uid = null,
    )
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } returns listOf(
      buildRemoteEntity(
        id = "log-dirty",
        payload = remotePayload,
        remoteTsMs = 5000L,
      )
    )

    val result = runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    assertThat(result).isTrue()
    val row = db.schemaQueries.selectOneForSync(
      TEST_KIND,
      TEST_SCOPE.toPath(),
      "log-dirty"
    )
      .executeAsOne()
    assertThat(row.dirty).isTrue()
    assertThat(row.payload.asList()).containsExactly(0x11.toByte())
    assertThat(row.remote_updated_at).isNull()
    assertThat(
      cursors.get(
        TEST_UID,
        TEST_KIND,
        TEST_SCOPE
      )!!.lastSeenRemote
    ).isEqualTo(5000L)
  }

  @Test
  fun runFor_success_marksCursorHydratedTrue() = runTest {
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } returns listOf(
      buildRemoteEntity(
        id = "log-a",
        remoteTsMs = 5000L
      ),
    )

    runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = cursors.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor).isNotNull()
    assertThat(cursor!!.hydrated).isTrue()
    assertThat(cursor.failedAttempts).isEqualTo(0)
  }

  @Test
  fun runFor_success_cursorLastSeenRemoteIsMaxTs() = runTest {
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } returns listOf(
      buildRemoteEntity(
        id = "log-x",
        remoteTsMs = 1000L
      ),
      buildRemoteEntity(
        id = "log-y",
        remoteTsMs = 9000L
      ),
      buildRemoteEntity(
        id = "log-z",
        remoteTsMs = 4000L
      ),
    )

    runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = cursors.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.lastSeenRemote).isEqualTo(9000L)
  }

  @Test
  fun runFor_emptyCollection_returnsTrueAndCursorHydratedWithNullLastSeen() =
    runTest {
      coEvery {
        fetcher.fetchAll(
          TEST_KIND,
          TEST_SCOPE
        )
      } returns emptyList()

      val result = runner.runFor(
        TEST_UID,
        TEST_KIND,
        TEST_SCOPE
      )

      assertThat(result).isTrue()
      val cursor = cursors.get(
        TEST_UID,
        TEST_KIND,
        TEST_SCOPE
      )
      assertThat(cursor).isNotNull()
      assertThat(cursor!!.hydrated).isTrue()
      assertThat(cursor.lastSeenRemote).isNull()
      assertThat(cursor.failedAttempts).isEqualTo(0)
    }

  // Failure: RemoteFetcher throws → failed_attempts++, last_attempt_at set, hydrated stays false.

  @Test
  fun runFor_fetcherThrows_returnsFalse() = runTest {
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } throws RuntimeException("network error")

    val result = runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    assertThat(result).isFalse()
  }

  @Test
  fun runFor_fetcherThrows_incrementsFailedAttempts() = runTest {
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } throws RuntimeException("timeout")

    runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = cursors.get(
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
  fun runFor_fetcherThrowsTwice_failedAttemptsAccumulates() = runTest {
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    } throws RuntimeException("repeated failure")

    runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )

    val cursor = cursors.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.failedAttempts).isEqualTo(2)
    assertThat(cursor.hydrated).isFalse()
  }

  @Test
  fun runFor_retryAfterFailureSucceeds_resetsFailedAttempts() = runTest {
    val docs = listOf(
      buildRemoteEntity(
        id = "log-retry",
        remoteTsMs = 1000L
      )
    )
    coEvery {
      fetcher.fetchAll(
        TEST_KIND,
        TEST_SCOPE
      )
    }
      .throws(RuntimeException("first attempt fails"))
      .andThen(docs)

    runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    ) // fails
    val result = runner.runFor(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    ) // succeeds

    assertThat(result).isTrue()
    val cursor = cursors.get(
      TEST_UID,
      TEST_KIND,
      TEST_SCOPE
    )
    assertThat(cursor!!.hydrated).isTrue()
    assertThat(cursor.failedAttempts).isEqualTo(0)
  }

  // --- helpers ---

  private fun buildRemoteEntity(
    id: String = "doc-1",
    remoteTsMs: Long = 1000L,
    payload: ByteArray = byteArrayOf(0x42),
    deleted: Boolean = false,
  ): RemoteEntity = RemoteEntity(
    id = id,
    payload = payload,
    deleted = deleted,
    remoteTsMs = remoteTsMs,
  )
}
