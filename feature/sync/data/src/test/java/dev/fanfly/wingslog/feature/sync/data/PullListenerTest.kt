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

private const val TEST_USER_ID = "user-pull-001"
private const val TEST_AIRCRAFT_ID = "aircraft-pull-001"
private val TEST_SCOPE =
  EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID)
private val TEST_KIND = CollectionKind.MaintenanceLog

class PullListenerTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var listener: PullListener

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    listener = PullListener(kind = TEST_KIND, scope = TEST_SCOPE, db = db)
  }

  // Case 1: no local row — remote arrives — row inserted with dirty=0 and remote_updated_at=remoteTs

  @Test
  fun apply_noLocalRow_insertsRowWithDirtyFalseAndRemoteTs() = runTest {
    val remote = buildRemoteEntity(id = "log-1", remoteTsMs = 5000L)

    listener.apply(remote)

    val row =
      db.schemaQueries.selectOneForSync(TEST_KIND, TEST_SCOPE.toPath(), "log-1")
        .executeAsOneOrNull()
    assertThat(row).isNotNull()
    assertThat(row!!.dirty).isFalse()
    assertThat(row.remote_updated_at).isEqualTo(5000L)
    assertThat(row.payload.contentEquals(remote.payload)).isTrue()
  }

  @Test
  fun apply_noLocalRow_returnsRemoteTs() = runTest {
    val remote = buildRemoteEntity(id = "log-x", remoteTsMs = 9999L)

    val returned = listener.apply(remote)

    assertThat(returned).isEqualTo(9999L)
  }

  // Case 2: local dirty=1 — remote arrives — local payload unchanged, dirty still 1

  @Test
  fun apply_localIsDirty_dropsRemote_localPayloadUnchanged() = runTest {
    val originalPayload = byteArrayOf(0x01, 0x02)
    insertEntityRow(
      id = "log-2",
      payload = originalPayload,
      remoteTsMs = null,
      dirty = true,
    )
    val remote = buildRemoteEntity(
      id = "log-2",
      remoteTsMs = 9000L,
      payload = byteArrayOf(0xFF.toByte())
    )

    listener.apply(remote)

    val row =
      db.schemaQueries.selectOneForSync(TEST_KIND, TEST_SCOPE.toPath(), "log-2")
        .executeAsOneOrNull()
    assertThat(row).isNotNull()
    assertThat(row!!.dirty).isTrue()
    assertThat(row.payload.contentEquals(originalPayload)).isTrue()
  }

  @Test
  fun apply_localIsDirty_returnsRemoteTsEvenThoughDropped() = runTest {
    insertEntityRow(
      id = "log-d",
      payload = byteArrayOf(0x01),
      remoteTsMs = null,
      dirty = true
    )
    val remote = buildRemoteEntity(id = "log-d", remoteTsMs = 7777L)

    val returned = listener.apply(remote)

    assertThat(returned).isEqualTo(7777L)
  }

  // Case 3: local dirty=0, remote_updated_at=1000, remote arrives with update_time=2000
  //         → local overwritten, remote_updated_at=2000

  @Test
  fun apply_remoteStrictlyNewer_overwritesLocalRow() = runTest {
    val oldPayload = byteArrayOf(0xAA.toByte())
    val newPayload = byteArrayOf(0xBB.toByte())
    insertEntityRow(
      id = "log-3",
      payload = oldPayload,
      remoteTsMs = 1000L,
      dirty = false
    )
    val remote =
      buildRemoteEntity(id = "log-3", remoteTsMs = 2000L, payload = newPayload)

    listener.apply(remote)

    val row =
      db.schemaQueries.selectOneForSync(TEST_KIND, TEST_SCOPE.toPath(), "log-3")
        .executeAsOneOrNull()
    assertThat(row).isNotNull()
    assertThat(row!!.remote_updated_at).isEqualTo(2000L)
    assertThat(row.dirty).isFalse()
    assertThat(row.payload.contentEquals(newPayload)).isTrue()
  }

  // Case 4: local dirty=0, remote_updated_at=2000, remote arrives with update_time=1500
  //         → local untouched

  @Test
  fun apply_remoteIsStale_localUntouched() = runTest {
    val localPayload = byteArrayOf(0xCC.toByte())
    insertEntityRow(
      id = "log-4",
      payload = localPayload,
      remoteTsMs = 2000L,
      dirty = false
    )
    val remote = buildRemoteEntity(
      id = "log-4",
      remoteTsMs = 1500L,
      payload = byteArrayOf(0xDD.toByte())
    )

    listener.apply(remote)

    val row =
      db.schemaQueries.selectOneForSync(TEST_KIND, TEST_SCOPE.toPath(), "log-4")
        .executeAsOneOrNull()
    assertThat(row).isNotNull()
    assertThat(row!!.remote_updated_at).isEqualTo(2000L)
    assertThat(row.payload.contentEquals(localPayload)).isTrue()
  }

  @Test
  fun apply_remoteIsStale_returnsRemoteTsNotLocalTs() = runTest {
    insertEntityRow(
      id = "log-s",
      payload = byteArrayOf(0x01),
      remoteTsMs = 2000L,
      dirty = false
    )
    val remote = buildRemoteEntity(id = "log-s", remoteTsMs = 1500L)

    val returned = listener.apply(remote)

    // apply() always returns remote.remoteTsMs regardless of comparator outcome
    assertThat(returned).isEqualTo(1500L)
  }

  // --- helpers ---

  private suspend fun insertEntityRow(
    id: String,
    payload: ByteArray,
    remoteTsMs: Long?,
    dirty: Boolean,
  ) {
    db.schemaQueries.upsert(
      collection = TEST_KIND,
      scope_path = TEST_SCOPE.toPath(),
      id = id,
      payload = payload,
      payload_schema = TEST_KIND.schemaName,
      updated_at = 1000L,
      remote_updated_at = remoteTsMs,
      dirty = dirty,
      deleted = false,
      writer_uid = null,
    )
  }

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
