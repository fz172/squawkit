package dev.fanfly.wingslog.core.storage.blob

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityRef
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BlobReferenceScannerTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var scanner: BlobReferenceScanner

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    scanner = BlobReferenceScanner(db)
  }

  @Test
  fun `collects the blobs live records name, across kinds and Things in the account`() =
    runTest {
      putLog("log-1", THING_A, listOf("photo"))
      putTask("task-1", THING_B, listOf("manual"))

      val refs = scanner.referencedUnder(setOf(USER_ROOT))

      assertThat(refs.referenced).containsExactly(
        BlobId("photo"),
        BlobId("manual")
      )
      assertThat(refs.isFree(BlobId("photo"))).isFalse()
      assertThat(refs.isFree(BlobId("unnamed"))).isTrue()
    }

  @Test
  fun `ignores tombstones and other accounts`() = runTest {
    putLog("dead", THING_A, listOf("gone"), deleted = true)
    putLog(
      "theirs",
      EntityScope.thingChildUnsafe("someone-else", "t9")
        .toPath(),
      listOf("theirs")
    )

    assertThat(scanner.referencedUnder(setOf(USER_ROOT)).referenced).isEmpty()
  }

  @Test
  fun `leaves out the releasing record, but not another that cites the same document`() =
    runTest {
      // Two tasks cite one manual. The first task's edit drops it; its old payload still names it.
      putTask("task-1", THING_A, listOf("manual"))
      putTask("task-2", THING_A, listOf("manual"))
      val owner = EntityRef(CollectionKind.MaintenanceTask, "task-1")

      assertThat(
        scanner.referencedUnder(setOf(USER_ROOT), excluding = owner)
          .isFree(BlobId("manual"))
      ).isFalse()

      putTask("task-2", THING_A, emptyList())
      assertThat(
        scanner.referencedUnder(setOf(USER_ROOT), excluding = owner)
          .isFree(BlobId("manual"))
      ).isTrue()
    }

  @Test
  fun `exclusion matches kind as well as id`() = runTest {
    putLog("same-id", THING_A, listOf("photo"))

    val wrongKind = EntityRef(CollectionKind.MaintenanceTask, "same-id")

    assertThat(
      scanner.referencedUnder(setOf(USER_ROOT), excluding = wrongKind)
        .isFree(BlobId("photo"))
    ).isFalse()
  }

  @Test
  fun `an undecodable live payload makes nothing free`() = runTest {
    putEntity(
      CollectionKind.MaintenanceLog,
      THING_A,
      "broken",
      byteArrayOf(0xff.toByte(), 0xff.toByte())
    )

    val refs = scanner.referencedUnder(setOf(USER_ROOT))

    assertThat(refs.undecodable).isEqualTo(1)
    assertThat(refs.isFree(BlobId("anything"))).isFalse()
  }

  @Test
  fun `finds the account root of a Thing scope`() {
    assertThat(BlobReferenceScanner.userRootOf(THING_A)).isEqualTo(USER_ROOT)
    assertThat(BlobReferenceScanner.userRootOf("/other/path/")).isEqualTo("/other/path/")
  }

  private suspend fun putLog(
    id: String,
    scope: String,
    blobs: List<String>,
    deleted: Boolean = false
  ) =
    putEntity(
      CollectionKind.MaintenanceLog,
      scope,
      id,
      MaintenanceLog(
        id = id,
        attachments = blobs.map {
          Attachment(
            id = it,
            sha256 = SHA
          )
        }).encode(),
      deleted,
    )

  private suspend fun putTask(id: String, scope: String, blobs: List<String>) =
    putEntity(
      CollectionKind.MaintenanceTask,
      scope,
      id,
      MaintenanceTask(
        id = id,
        attachments = blobs.map {
          Attachment(
            id = it,
            sha256 = SHA
          )
        }).encode(),
    )

  private suspend fun putEntity(
    kind: CollectionKind,
    scope: String,
    id: String,
    payload: ByteArray,
    deleted: Boolean = false,
  ) {
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope,
      id = id,
      payload = payload,
      payload_schema = kind.schemaName,
      updated_at = 0L,
      remote_updated_at = 0L,
      dirty = false,
      deleted = deleted,
      writer_uid = null,
    )
  }

  private companion object {
    const val UID = "u1"
    const val SHA =
      "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
    val USER_ROOT = EntityScope.userRoot(UID)
      .toPath()
    val THING_A = EntityScope.thingChildUnsafe(UID, "thing-a")
      .toPath()
    val THING_B = EntityScope.thingChildUnsafe(UID, "thing-b")
      .toPath()
  }
}
