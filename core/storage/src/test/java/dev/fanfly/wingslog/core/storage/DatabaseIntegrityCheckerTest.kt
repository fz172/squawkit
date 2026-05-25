package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val TEST_UID = "uid123"
private const val OTHER_UID = "uid-other-456"

class DatabaseIntegrityCheckerTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var checker: DatabaseIntegrityChecker

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    // Schema is async-generated; the sync JVM driver wraps it via .synchronous().
    WingsLogDatabase.Schema.synchronous().create(driver)
    db = createWingsLogDatabase(driver)
    checker = DatabaseIntegrityChecker(db, driver)
  }

  // ---- wipeDataForUser ----

  @Test
  fun wipeDataForUser_deletesEntitiesAndCursorsForUid() = runTest {
    // Insert an entity row in the target user's scope.
    db.schemaQueries.upsert(
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$TEST_UID/fleet",
      id = "aircraft-1",
      payload = byteArrayOf(1, 2, 3),
      payload_schema = "Aircraft",
      updated_at = 1_000_000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
    )
    // Insert a sync_cursor row for the target user.
    db.schemaQueries.upsertCursor(
      uid = TEST_UID,
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$TEST_UID/fleet",
      hydrated = true,
      last_seen_remote = null,
      failed_attempts = 0L,
      last_attempt_at = null,
    )

    checker.wipeDataForUser(TEST_UID)

    val entitiesAfter = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$TEST_UID/fleet",
    ).awaitAsList()
    assertThat(entitiesAfter).isEmpty()

    val cursorsAfter = db.schemaQueries.selectCursor(
      uid = TEST_UID,
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$TEST_UID/fleet",
    ).awaitAsOneOrNull()
    assertThat(cursorsAfter).isNull()
  }

  @Test
  fun wipeDataForUser_doesNotDeleteEntitiesForOtherUser() = runTest {
    // Insert entity for target user and a different user.
    db.schemaQueries.upsert(
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$TEST_UID/fleet",
      id = "aircraft-target",
      payload = byteArrayOf(1),
      payload_schema = "Aircraft",
      updated_at = 1_000_000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
    )
    db.schemaQueries.upsert(
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$OTHER_UID/fleet",
      id = "aircraft-other",
      payload = byteArrayOf(2),
      payload_schema = "Aircraft",
      updated_at = 1_000_000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
    )

    checker.wipeDataForUser(TEST_UID)

    val otherUserEntities = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$OTHER_UID/fleet",
    ).awaitAsList()
    assertThat(otherUserEntities).hasSize(1)
    assertThat(otherUserEntities[0].id).isEqualTo("aircraft-other")
  }

  @Test
  fun wipeDataForUser_doesNotDeleteCursorsForOtherUser() = runTest {
    db.schemaQueries.upsertCursor(
      uid = TEST_UID,
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$TEST_UID/fleet",
      hydrated = true,
      last_seen_remote = null,
      failed_attempts = 0L,
      last_attempt_at = null,
    )
    db.schemaQueries.upsertCursor(
      uid = OTHER_UID,
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$OTHER_UID/fleet",
      hydrated = true,
      last_seen_remote = null,
      failed_attempts = 0L,
      last_attempt_at = null,
    )

    checker.wipeDataForUser(TEST_UID)

    val otherCursor = db.schemaQueries.selectCursor(
      uid = OTHER_UID,
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$OTHER_UID/fleet",
    ).awaitAsOneOrNull()
    assertThat(otherCursor).isNotNull()
  }

  @Test
  fun wipeDataForUser_isNoopWhenUserHasNoData() = runTest {
    // Call with a uid that has no rows — must not throw.
    checker.wipeDataForUser("nonexistent-uid")
    // If we reach here without exception the test passes.
  }

  @Test
  fun wipeDataForUser_doesNotWipeUnrelatedScopePaths() = runTest {
    // A scope_path that contains the uid string but doesn't start with /users/<uid>/
    // (e.g. someone else's path that happens to mention our uid in a sub-path)
    // SQLite LIKE is used with prefix "/users/<uid>/%" so only exact prefix matches should go.
    db.schemaQueries.upsert(
      collection = CollectionKind.Aircraft,
      scope_path = "/users/$OTHER_UID/references/$TEST_UID/extra",
      id = "aircraft-unrelated",
      payload = byteArrayOf(9),
      payload_schema = "Aircraft",
      updated_at = 1_000_000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
    )

    checker.wipeDataForUser(TEST_UID)

    val remaining = db.schemaQueries.selectAll(
      collection = CollectionKind.Aircraft,
      scope = "/users/$OTHER_UID/references/$TEST_UID/extra",
    ).awaitAsList()
    assertThat(remaining).hasSize(1)
  }
}
