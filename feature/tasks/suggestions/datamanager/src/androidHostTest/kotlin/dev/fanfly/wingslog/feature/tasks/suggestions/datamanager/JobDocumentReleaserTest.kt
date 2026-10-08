package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.core.storage.AiJobDocument
import dev.fanfly.wingslog.core.storage.AiJobDocumentStore
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.thing.Attachment
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobDocumentReleaserTest {

  private val manual = Attachment(id = "blob-1", name = "POH.pdf")
  private val bulletin = Attachment(id = "blob-2", name = "SB 12.pdf")

  private val store = mockk<AiJobDocumentStore>(relaxed = true)
  private val client = mockk<AiJobClient>()
  private val attachments = mockk<AttachmentManager>(relaxed = true)
  private var uid: String? = "alice"

  private val releaser = JobDocumentReleaser(
    store = store,
    currentUid = { uid },
    client = { client },
    attachments = { attachments },
    foreground = AppForegroundObserver(),
  )

  private val file =
    PickedFile(uri = "file://POH.pdf", name = "POH.pdf", mimeType = "application/pdf", sizeBytes = 10)

  /** A releaser whose own work runs on [scope], so a test sees it finish. */
  private fun releaser(scope: CoroutineScope) = JobDocumentReleaser(
    store = store,
    currentUid = { uid },
    client = { client },
    attachments = { attachments },
    foreground = AppForegroundObserver(),
    scope = scope,
  )

  @Test
  fun `notes a run's documents under the signed-in user`() = runTest {
    releaser.record(AiJobId("job-1"), "thing-1", listOf(manual))

    coVerify { store.record("alice", "job-1", "thing-1", listOf(manual)) }
  }

  @Test
  fun `notes nothing for a run without documents, or signed out`() = runTest {
    releaser.record(AiJobId("job-1"), "thing-1", emptyList())
    uid = null
    releaser.record(AiJobId("job-1"), "thing-1", listOf(manual))

    coVerify(exactly = 0) { store.record(any(), any(), any(), any()) }
  }

  @Test
  fun `hands back a run's documents`() = runTest {
    coEvery { store.forJob("alice", "job-1") } returns
      listOf(AiJobDocument("alice", "job-1", "thing-1", manual))

    assertThat(releaser.documentsOf(AiJobId("job-1"))).containsExactly(manual)
    uid = null
    assertThat(releaser.documentsOf(AiJobId("job-1"))).isEmpty()
  }

  @Test
  fun `releases a run's documents, then forgets them`() = runTest {
    coEvery { store.forJob("alice", "job-1") } returns listOf(
      AiJobDocument("alice", "job-1", "thing-1", manual),
      AiJobDocument("alice", "job-1", "thing-1", bulletin),
    )

    releaser.release(AiJobId("job-1"))

    coVerify {
      attachments.release(manual, null)
      attachments.release(bulletin, null)
      store.forget("alice", "job-1")
    }
  }

  @Test
  fun `releases runs the server no longer has, and keeps live ones`() = runTest {
    coEvery { store.all() } returns listOf(
      AiJobDocument("alice", "gone", "thing-1", manual),
      AiJobDocument("alice", "live", "thing-1", bulletin),
    )
    coEvery { client.isGone(AiJobId("gone")) } returns true
    coEvery { client.isGone(AiJobId("live")) } returns false

    releaser.releaseGone()

    coVerify { attachments.release(manual, null) }
    coVerify { store.forget("alice", "gone") }
    coVerify(exactly = 0) { attachments.release(bulletin, any()) }
    coVerify(exactly = 0) { store.forget("alice", "live") }
  }

  @Test
  fun `releases another user's runs without asking the server`() = runTest {
    coEvery { store.all() } returns listOf(AiJobDocument("bob", "job-9", "thing-9", manual))

    releaser.releaseGone()

    coVerify { attachments.release(manual, null) }
    coVerify { store.forget("bob", "job-9") }
    coVerify(exactly = 0) { client.isGone(any()) }
  }

  @Test
  fun `a picked file is stored, and noted as picked until a run takes it`() = runTest {
    val releaser = releaser(this)
    coEvery { attachments.addPickedFile("thing-1", file, "POH.pdf", 100) } returns manual

    assertThat(releaser.pick("thing-1", file, maxBytes = 100).getOrNull()).isEqualTo(manual)
    coVerify { store.record("alice", "picked", "thing-1", listOf(manual)) }

    releaser.record(AiJobId("job-1"), "thing-1", listOf(manual))
    coVerify { store.record("alice", "job-1", "thing-1", listOf(manual)) }
    coVerify { store.forget("alice", "picked", listOf("blob-1")) }
  }

  @Test
  fun `a file that could not be stored is not noted`() = runTest {
    val releaser = releaser(this)
    coEvery { attachments.addPickedFile(any(), any(), any(), any()) } throws RuntimeException("disk")

    assertThat(releaser.pick("thing-1", file, maxBytes = 100).isFailure).isTrue()
    coVerify(exactly = 0) { store.record(any(), any(), any(), any()) }
  }

  @Test
  fun `letting go releases a picked document and forgets it, on its own scope`() = runTest {
    val releaser = releaser(this)

    releaser.letGo(listOf(manual, bulletin))
    coVerify(exactly = 0) { attachments.release(any(), any()) }
    runCurrent()

    coVerify {
      attachments.release(manual, null)
      attachments.release(bulletin, null)
      store.forget("alice", "picked", listOf("blob-1", "blob-2"))
    }
  }

  @Test
  fun `a file still being stored when its caller goes away is let go once stored`() = runTest {
    val releaser = releaser(this)
    val stored = CompletableDeferred<Attachment>()
    coEvery { attachments.addPickedFile(any(), any(), any(), any()) } coAnswers { stored.await() }

    val picking = launch { releaser.pick("thing-1", file, maxBytes = 100) }
    runCurrent()
    picking.cancel()
    stored.complete(manual)
    runCurrent()

    coVerify { attachments.release(manual, null) }
  }

  @Test
  fun `a new session lets go of files a dead process picked, and keeps this one's`() = runTest {
    val releaser = releaser(this)
    coEvery { attachments.addPickedFile("thing-1", file, "POH.pdf", 100) } returns manual
    releaser.pick("thing-1", file, maxBytes = 100)
    coEvery { store.all() } returns listOf(
      AiJobDocument("alice", "picked", "thing-1", manual),
      AiJobDocument("alice", "picked", "thing-1", bulletin),
    )

    releaser.releaseGone()

    coVerify { attachments.release(bulletin, null) }
    coVerify { store.forget("alice", "picked", listOf("blob-2")) }
    coVerify(exactly = 0) { attachments.release(manual, any()) }
    // Picked files belong to no job, so the server is never asked about one.
    coVerify(exactly = 0) { client.isGone(any()) }
  }

  @Test
  fun `does nothing signed out`() = runTest {
    uid = null

    releaser.releaseGone()

    coVerify(exactly = 0) { store.all() }
  }
}
