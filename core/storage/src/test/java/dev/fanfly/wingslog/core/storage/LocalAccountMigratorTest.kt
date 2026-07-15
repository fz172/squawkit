package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.test.runTest
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
    // Schema is async-generated; the sync JVM driver wraps it via .synchronous().
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    migrator = LocalAccountMigratorImpl(db)
  }

  private suspend fun insertEntity(
    uid: String,
    id: String,
    dirty: Boolean,
    remoteUpdatedAt: Long?
  ) {
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
      writer_uid = null,
    )
  }

  @Test
  fun reassign_movesEntityToNewScope_marksDirty_clearsRemoteTimestamp() =
    runTest {
      insertEntity(FROM_UID, "ac-1", dirty = false, remoteUpdatedAt = 5_000L)

      migrator.reassign(FROM_UID, TO_UID)

      val oldScope = db.schemaQueries.selectAll(
        collection = CollectionKind.Aircraft,
        scope = "/users/$FROM_UID/fleet",
      )
        .awaitAsList()
      assertThat(oldScope).isEmpty()

      val moved = db.schemaQueries.selectOneForSync(
        collection = CollectionKind.Aircraft,
        scope = "/users/$TO_UID/fleet",
        id = "ac-1",
      )
        .awaitAsOneOrNull()
      assertThat(moved).isNotNull()
      assertThat(moved!!.dirty).isTrue()
      assertThat(moved.remote_updated_at).isNull()
    }

  @Test
  fun reassign_leavesOtherUsersDataUntouched() = runTest {
    insertEntity(FROM_UID, "ac-from", dirty = false, remoteUpdatedAt = null)
    insertEntity(
      "someone-else",
      "ac-other",
      dirty = false,
      remoteUpdatedAt = null
    )

    migrator.reassign(FROM_UID, TO_UID)

    val other = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/someone-else/fleet",
    )
      .awaitAsList()
    assertThat(other).hasSize(1)
    assertThat(other[0].id).isEqualTo("ac-other")
  }

  @Test
  fun reassign_resetsBlobScopeAndRemoteState() = runTest {
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

    migrator.reassign(FROM_UID, TO_UID)

    val blob = db.schemaQueries.selectBlobById("blob-1")
      .awaitAsOneOrNull()
    assertThat(blob).isNotNull()
    assertThat(blob!!.scope_path).isEqualTo("/users/$TO_UID/fleet")
    assertThat(blob.remote_state).isEqualTo(RemoteState.LocalOnly)
    assertThat(blob.remote_path).isNull()
    assertThat(blob.upload_attempts).isEqualTo(0L)
  }

  @Test
  fun reassign_dropsSyncCursorsForBothUids() = runTest {
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

    migrator.reassign(FROM_UID, TO_UID)

    listOf(FROM_UID, TO_UID).forEach { uid ->
      val cursor = db.schemaQueries.selectCursor(
        uid = uid,
        collection = CollectionKind.Aircraft,
        scope_path = "/users/$uid/fleet",
      )
        .awaitAsOneOrNull()
      assertThat(cursor).isNull()
    }
  }

  @Test
  fun reassign_isIdempotent() = runTest {
    insertEntity(FROM_UID, "ac-1", dirty = false, remoteUpdatedAt = null)

    migrator.reassign(FROM_UID, TO_UID)
    migrator.reassign(FROM_UID, TO_UID)

    val moved = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$TO_UID/fleet",
    )
      .awaitAsList()
    assertThat(moved).hasSize(1)
  }

  /** The per-user singleton: one row per account, always at the same id. */
  private suspend fun insertUserInfo(uid: String, payload: Byte) {
    db.schemaQueries.upsert(
      collection = CollectionKind.UserInfo,
      scope_path = "/users/$uid/",
      id = "main",
      payload = byteArrayOf(payload),
      payload_schema = CollectionKind.UserInfo.schemaName,
      updated_at = 1_000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
      writer_uid = null,
    )
  }

  @Test
  fun reassign_dropsGuestUserInfo_keepingTheAccountsOwn() = runTest {
    // Issue #222: both accounts have a UserInfo row, and both live at id "main" — so moving the
    // guest's onto the account's scope hits the (collection, scope_path, id) primary key and the
    // merge dies with a UNIQUE constraint failure. The account's identity is the one that survives.
    insertUserInfo(FROM_UID, payload = 1)
    insertUserInfo(TO_UID, payload = 2)

    migrator.reassign(FROM_UID, TO_UID)

    val kept = db.schemaQueries.selectOne(
      collection = CollectionKind.UserInfo,
      scope = "/users/$TO_UID/",
      id = "main",
    )
      .awaitAsOneOrNull()
    assertThat(kept).isNotNull()
    assertThat(kept!!.payload).isEqualTo(byteArrayOf(2))
    assertThat(
      db.schemaQueries.selectAll(CollectionKind.UserInfo, "/users/$FROM_UID/")
        .awaitAsList()
    ).isEmpty()
  }

  @Test
  fun reassign_dropsGuestFeatureLab_notInheritedIntoTheAccount() = runTest {
    // FeatureLab is a per-user singleton at the fixed id "main", like UserInfo. A guest's
    // experimental toggles must not ride the merge into a real account: the guest's row is dropped,
    // not moved (moving it would override the account's own device flags — and collide when the
    // account already has a local copy).
    db.schemaQueries.upsert(
      collection = CollectionKind.FeatureLab,
      scope_path = "/users/$FROM_UID/",
      id = "main",
      payload = byteArrayOf(1),
      payload_schema = CollectionKind.FeatureLab.schemaName,
      updated_at = 1_000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
      writer_uid = null,
    )

    migrator.reassign(FROM_UID, TO_UID)

    assertThat(
      db.schemaQueries.selectAll(CollectionKind.FeatureLab, "/users/$FROM_UID/")
        .awaitAsList()
    ).isEmpty()
    assertThat(
      db.schemaQueries.selectAll(CollectionKind.FeatureLab, "/users/$TO_UID/")
        .awaitAsList()
    ).isEmpty()
  }

  @Test
  fun reassign_dropsGuestUserInfo_evenWhenTheAccountHasNoneLocally() = runTest {
    // The account's UserInfo may still be in the cloud rather than on this device. The guest's must
    // not be promoted into its place: it points at the guest's self-technician, and hydration would
    // never overwrite it (it arrives dirty), so the account would silently take on the guest's
    // identity. ensureSelfProfile() re-seeds a missing one from the account itself.
    insertUserInfo(FROM_UID, payload = 1)

    migrator.reassign(FROM_UID, TO_UID)

    assertThat(
      db.schemaQueries.selectAll(CollectionKind.UserInfo, "/users/$TO_UID/")
        .awaitAsList()
    ).isEmpty()
  }

  @Test
  fun reassign_dropsRecordsTheDestinationAlreadyHolds() = runTest {
    // A same-id row in both scopes is the same record; the destination's copy is the one the account
    // owns. Without this the UPDATE trips the primary key and the whole merge crashes.
    insertEntity(FROM_UID, "ac-1", dirty = true, remoteUpdatedAt = null)
    insertEntity(TO_UID, "ac-1", dirty = false, remoteUpdatedAt = 9_000L)
    insertEntity(FROM_UID, "ac-2", dirty = true, remoteUpdatedAt = null)

    migrator.reassign(FROM_UID, TO_UID)

    val destination = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$TO_UID/fleet",
    )
      .awaitAsList()
    // The conflicting row survives once — as the destination's copy — and the rest still moves.
    assertThat(destination.map { it.id }).containsExactly("ac-1", "ac-2")
    val kept = db.schemaQueries.selectOneForSync(
      collection = CollectionKind.Aircraft,
      scope = "/users/$TO_UID/fleet",
      id = "ac-1",
    )
      .awaitAsOneOrNull()
    assertThat(kept!!.remote_updated_at).isEqualTo(9_000L)
    assertThat(
      db.schemaQueries.selectAll(CollectionKind.Aircraft, "/users/$FROM_UID/fleet")
        .awaitAsList()
    ).isEmpty()
  }

  @Test
  fun reassign_isNoopWhenUidsAreEqual() = runTest {
    insertEntity(FROM_UID, "ac-1", dirty = false, remoteUpdatedAt = null)

    migrator.reassign(FROM_UID, FROM_UID)

    val stillThere = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$FROM_UID/fleet",
    )
      .awaitAsList()
    assertThat(stillThere).hasSize(1)
  }
}
