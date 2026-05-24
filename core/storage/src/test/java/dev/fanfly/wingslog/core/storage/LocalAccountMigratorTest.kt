package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

private const val FROM_UID = "guest-abc"
private const val TO_UID = "account-xyz"

class LocalAccountMigratorTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var migrator: LocalAccountMigrator

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.create(driver)
    db = createWingsLogDatabase(driver)
    migrator = LocalAccountMigratorImpl(db)
  }

  private fun insertEntity(uid: String, id: String, dirty: Boolean, remoteUpdatedAt: Long?) {
    db.schemaQueries.upsert(
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$uid/fleet",
      id = id,
      payload = byteArrayOf(1),
      payload_schema = "Aircraft",
      updated_at = 1_000L,
      remote_updated_at = remoteUpdatedAt,
      dirty = dirty,
      deleted = false,
    )
  }

  @Test
  fun reassign_movesEntityToNewScope_marksDirty_clearsRemoteTimestamp() = runBlocking {
    insertEntity(FROM_UID, "ac-1", dirty = false, remoteUpdatedAt = 5_000L)

    migrator.reassign(FROM_UID, TO_UID)

    val oldScope = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$FROM_UID/fleet",
    ).executeAsList()
    assertThat(oldScope).isEmpty()

    val moved = db.schemaQueries.selectOneForSync(
      collection = CollectionKind.Aircraft,
      scope = "/users/$TO_UID/fleet",
      id = "ac-1",
    ).executeAsOneOrNull()
    assertThat(moved).isNotNull()
    assertThat(moved!!.dirty).isTrue()
    assertThat(moved.remote_updated_at).isNull()
  }

  @Test
  fun reassign_leavesOtherUsersDataUntouched() {
    insertEntity(FROM_UID, "ac-from", dirty = false, remoteUpdatedAt = null)
    insertEntity("someone-else", "ac-other", dirty = false, remoteUpdatedAt = null)

    runBlocking { migrator.reassign(FROM_UID, TO_UID) }

    val other = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/someone-else/fleet",
    ).executeAsList()
    assertThat(other).hasSize(1)
    assertThat(other[0].id).isEqualTo("ac-other")
  }

  @Test
  fun reassign_resetsBlobScopeAndRemoteState() {
    db.schemaQueries.upsertBlob(
      id = "blob-1",
      scope_path = "/users/$FROM_UID/fleet",
      relative_path = "blobs/blob-1",
      content_type = "image/png",
      size_bytes = 10L,
      sha256 = "sha",
      remote_state = RemoteState.Synced,
      remote_path = "remote/blob-1",
      upload_attempts = 3L,
      last_attempt_at = 1L,
      updated_at = 1L,
      deleted = false,
    )

    runBlocking { migrator.reassign(FROM_UID, TO_UID) }

    val blob = db.schemaQueries.selectBlobById("blob-1").executeAsOneOrNull()
    assertThat(blob).isNotNull()
    assertThat(blob!!.scope_path).isEqualTo("/users/$TO_UID/fleet")
    assertThat(blob.remote_state).isEqualTo(RemoteState.LocalOnly)
    assertThat(blob.remote_path).isNull()
    assertThat(blob.upload_attempts).isEqualTo(0L)
  }

  @Test
  fun reassign_dropsSyncCursorsForBothUids() {
    listOf(FROM_UID, TO_UID).forEach { uid ->
      db.schemaQueries.upsertCursor(
        uid = uid,
        collection = CollectionKind.Aircraft,
        scope_path = "/users/$uid/fleet",
        hydrated = true,
        last_seen_remote = null,
        failed_attempts = 0L,
        last_attempt_at = null,
      )
    }

    runBlocking { migrator.reassign(FROM_UID, TO_UID) }

    listOf(FROM_UID, TO_UID).forEach { uid ->
      val cursor = db.schemaQueries.selectCursor(
        uid = uid,
        collection = CollectionKind.Aircraft,
        scope_path = "/users/$uid/fleet",
      ).executeAsOneOrNull()
      assertThat(cursor).isNull()
    }
  }

  @Test
  fun reassign_isIdempotent() {
    insertEntity(FROM_UID, "ac-1", dirty = false, remoteUpdatedAt = null)

    runBlocking {
      migrator.reassign(FROM_UID, TO_UID)
      migrator.reassign(FROM_UID, TO_UID)
    }

    val moved = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$TO_UID/fleet",
    ).executeAsList()
    assertThat(moved).hasSize(1)
  }

  @Test
  fun reassign_isNoopWhenUidsAreEqual() {
    insertEntity(FROM_UID, "ac-1", dirty = false, remoteUpdatedAt = null)

    runBlocking { migrator.reassign(FROM_UID, FROM_UID) }

    val stillThere = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$FROM_UID/fleet",
    ).executeAsList()
    assertThat(stillThere).hasSize(1)
  }
}
