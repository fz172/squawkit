package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import org.junit.Before
import org.junit.Test

/**
 * The 7 → 8 migration: the local half of the Aircraft → Thing path move
 * (docs/product/thing_migration_design.md §4, `7.sqm`).
 *
 * Why this migration exists at all, given §4 says there is no local migrator: the design's answer
 * for local data is "delete and reinstall the app," which holds on Android and iOS but **not on
 * web** — `DriverFactory.js.kt` persists SQLite to OPFS and runs a version-aware `Schema.migrate`,
 * so a returning web user keeps their database. Without `7.sqm` their first read after the Phase 1
 * deploy throws, because `CollectionKind.fromWire` calls `error(...)` on an unregistered name and
 * every existing row says `'aircraft'`.
 *
 * Seeding goes through raw SQL rather than the typed queries on purpose: the generated API encodes
 * a `CollectionKind`, so it *cannot* write the legacy string this migration exists to find. Only
 * raw SQL can produce a genuinely pre-migration row. The tables themselves are unchanged across
 * this step — it moves data, not DDL — so a current-schema database seeded with legacy-shaped rows
 * is a faithful stand-in for a real pre-migration file.
 */
class ThingPathMigration7Test {

  private lateinit var driver: SqlDriver

  @Before
  fun setUp() {
    driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous().create(driver)
  }

  private fun exec(sql: String) = driver.execute(null, sql, 0)

  private fun queryOne(sql: String): String =
    driver.executeQuery(null, sql, { cursor ->
      cursor.next().value
      app.cash.sqldelight.db.QueryResult.Value(cursor.getString(0) ?: "")
    }, 0).value

  private fun count(sql: String): Long =
    driver.executeQuery(null, sql, { cursor ->
      cursor.next().value
      app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: 0L)
    }, 0).value

  /** Rows exactly as a pre-migration client wrote them. */
  private fun seedLegacyRows() {
    exec(
      """
      INSERT INTO entity(collection, scope_path, id, payload, payload_schema, updated_at, dirty, deleted)
      VALUES ('aircraft', '/users/u1/', 'ac1', x'0a03616331', 'aircraft.Aircraft', 100, 0, 0)
      """.trimIndent(),
    )
    exec(
      """
      INSERT INTO entity(collection, scope_path, id, payload, payload_schema, updated_at, dirty, deleted)
      VALUES ('maintenance_log', '/users/u1/aircraft/ac1/', 'log1', x'0a04', 'aircraft.MaintenanceLog', 101, 1, 0)
      """.trimIndent(),
    )
    exec(
      """
      INSERT INTO sync_cursor(uid, collection, scope_path, hydrated)
      VALUES ('u1', 'aircraft', '/users/u1/', 1),
             ('u1', 'maintenance_log', '/users/u1/aircraft/ac1/', 1)
      """.trimIndent(),
    )
    exec(
      """
      INSERT INTO urgency_watermark(uid, collection, scope_path, id, rank, updated_at)
      VALUES ('u1', 'maintenance_task', '/users/u1/aircraft/ac1/', 't1', 2, 100)
      """.trimIndent(),
    )
    exec(
      """
      INSERT INTO blob_object(id, scope_path, relative_path, size_bytes, sha256, remote_state,
                              remote_path, updated_at, deleted)
      VALUES ('b1', '/users/u1/aircraft/ac1/', 'blobs/b1.bin', 3, 'abc', 'Synced',
              'users/u1/aircraft/ac1/blobs/b1', 100, 0)
      """.trimIndent(),
    )
  }

  private fun migrate() {
    // SQLDelight applies `N.sqm` when migrating FROM version N, so `7.sqm` is the 7 → 8 step.
    // (The header comments on this repo's older .sqm files are off by one against that convention;
    // the empirical check is Schema.version, which is 8 with 7.sqm present.)
    WingsLogDatabase.Schema.synchronous().migrate(driver, 7, 8)
  }

  @Test
  fun theThingDocsCollectionAndSchemaBothMove() {
    seedLegacyRows()

    migrate()

    assertThat(queryOne("SELECT collection FROM entity WHERE id = 'ac1'")).isEqualTo("thing")
    assertThat(queryOne("SELECT payload_schema FROM entity WHERE id = 'ac1'"))
      .isEqualTo("thing.Thing")
  }

  @Test
  fun childScopePathsMove_whichIsASeparateConstantFromTheCollection() {
    // CollectionKind.wireName names the collection the Thing doc lives in; EntityScope names the
    // segment its children hang off. Two constants — rename one and half the tree relocates.
    seedLegacyRows()

    migrate()

    assertThat(queryOne("SELECT scope_path FROM entity WHERE id = 'log1'"))
      .isEqualTo("/users/u1/thing/ac1/")
    assertThat(queryOne("SELECT scope_path FROM sync_cursor WHERE collection = 'maintenance_log'"))
      .isEqualTo("/users/u1/thing/ac1/")
    assertThat(queryOne("SELECT scope_path FROM urgency_watermark WHERE id = 't1'"))
      .isEqualTo("/users/u1/thing/ac1/")
  }

  @Test
  fun blobsMoveOnBothColumns() {
    // remote_path is the one that is easy to miss, and missing it points every cached blob at an
    // object the backend has already moved.
    seedLegacyRows()

    migrate()

    assertThat(queryOne("SELECT scope_path FROM blob_object WHERE id = 'b1'"))
      .isEqualTo("/users/u1/thing/ac1/")
    assertThat(queryOne("SELECT remote_path FROM blob_object WHERE id = 'b1'"))
      .isEqualTo("users/u1/thing/ac1/blobs/b1")
  }

  @Test
  fun unrelatedKindsAndCursorsAreUntouched() {
    // Only the segment between {uid} and {acId} moves. maintenance_log stays maintenance_log — it
    // was never named after the aircraft.
    seedLegacyRows()

    migrate()

    assertThat(queryOne("SELECT collection FROM entity WHERE id = 'log1'"))
      .isEqualTo("maintenance_log")
    assertThat(queryOne("SELECT payload_schema FROM entity WHERE id = 'log1'"))
      .isEqualTo("aircraft.MaintenanceLog")
  }

  @Test
  fun dirtyAndDeletedFlagsSurvive() {
    // A dirty row must stay dirty: the local edit has not reached the backend, and clearing the
    // flag here would silently drop it.
    seedLegacyRows()

    migrate()

    assertThat(count("SELECT dirty FROM entity WHERE id = 'log1'")).isEqualTo(1L)
    assertThat(count("SELECT COUNT(*) FROM entity")).isEqualTo(2L)
    assertThat(count("SELECT COUNT(*) FROM sync_cursor")).isEqualTo(2L)
  }

  @Test
  fun cursorsAndWatermarksAreRewrittenNotDropped() {
    // Dropping cursors would force a full re-hydration of every aircraft's history on next launch;
    // dropping watermarks would re-arm every already-acknowledged overdue item as if newly crossed.
    seedLegacyRows()

    migrate()

    assertThat(count("SELECT hydrated FROM sync_cursor WHERE collection = 'thing'")).isEqualTo(1L)
    assertThat(count("SELECT rank FROM urgency_watermark WHERE id = 't1'")).isEqualTo(2L)
  }

  @Test
  fun runningItTwiceIsANoOp() {
    // Idempotent by construction — once no row matches '/aircraft/', every statement is a no-op.
    // Worth pinning because a device can arrive here already partly migrated.
    seedLegacyRows()

    migrate()
    val afterFirst = queryOne("SELECT scope_path FROM blob_object WHERE id = 'b1'")
    migrate()

    assertThat(queryOne("SELECT scope_path FROM blob_object WHERE id = 'b1'")).isEqualTo(afterFirst)
    assertThat(queryOne("SELECT collection FROM entity WHERE id = 'ac1'")).isEqualTo("thing")
    assertThat(count("SELECT COUNT(*) FROM entity")).isEqualTo(2L)
  }

  @Test
  fun aFreshInstallMigratesCleanlyWithNothingToDo() {
    // No seeded rows: the migration must not fail on an empty database.
    migrate()

    assertThat(count("SELECT COUNT(*) FROM entity")).isEqualTo(0L)
  }
}
