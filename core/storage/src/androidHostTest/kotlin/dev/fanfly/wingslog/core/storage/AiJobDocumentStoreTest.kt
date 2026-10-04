package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiJobDocumentStoreTest {

  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
    WingsLogDatabase.Schema.synchronous().create(it)
  }
  private val store = AiJobDocumentStore(createWingsLogDatabase(driver))

  private val manual = Attachment(
    id = "blob-1",
    name = "POH.pdf",
    type = AttachmentType.ATTACHMENT_TYPE_PDF,
    sha256 = "abc",
  )
  private val bulletin = manual.copy(id = "blob-2", name = "SB 12.pdf")

  @Test
  fun `keeps a job's documents whole, by user and job`() = runTest {
    store.record("alice", "job-1", "thing-1", listOf(manual, bulletin))
    store.record("alice", "job-2", "thing-1", listOf(bulletin))
    store.record("bob", "job-1", "thing-9", listOf(manual))

    assertThat(store.forJob("alice", "job-1")).containsExactly(
      AiJobDocument("alice", "job-1", "thing-1", manual),
      AiJobDocument("alice", "job-1", "thing-1", bulletin),
    )
    assertThat(store.all()).hasSize(4)
  }

  @Test
  fun `recording the same document twice keeps one row`() = runTest {
    store.record("alice", "job-1", "thing-1", listOf(manual))
    store.record("alice", "job-1", "thing-1", listOf(manual))

    assertThat(store.forJob("alice", "job-1")).hasSize(1)
  }

  @Test
  fun `forgets only the one job`() = runTest {
    store.record("alice", "job-1", "thing-1", listOf(manual))
    store.record("alice", "job-2", "thing-1", listOf(bulletin))
    store.record("bob", "job-1", "thing-9", listOf(manual))

    store.forget("alice", "job-1")

    assertThat(store.all().map { "${it.uid}/${it.jobId}" })
      .containsExactly("alice/job-2", "bob/job-1")
  }

  @Test
  fun `the migration from 8 adds the table`() = runTest {
    val old = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous().create(old)
    old.execute(null, "DROP TABLE ai_job_document", 0)

    WingsLogDatabase.Schema.synchronous().migrate(old, 8, 9)

    val migrated = AiJobDocumentStore(createWingsLogDatabase(old))
    migrated.record("alice", "job-1", "thing-1", listOf(manual))
    assertThat(migrated.forJob("alice", "job-1")).hasSize(1)
  }
}
