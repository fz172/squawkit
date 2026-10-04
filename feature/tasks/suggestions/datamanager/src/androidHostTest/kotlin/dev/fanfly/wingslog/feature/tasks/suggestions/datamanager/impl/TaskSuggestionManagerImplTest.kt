package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJob
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntitySyncObserver
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.JobDocumentReleaser
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionContextBuilder
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionMapper
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import dev.fanfly.wingslog.rpc.suggesttasks.LogSummary
import dev.fanfly.wingslog.rpc.suggesttasks.SourceDocumentRef
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksRequest
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestionContext
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.ByteString
import org.junit.Test
import kotlin.time.Instant

class TaskSuggestionManagerImplTest {

  private val template = ThingTemplate(id = "airplane", version = 13)
  private val client = mockk<AiJobClient>(relaxed = true)
  private val builder = mockk<SuggestionContextBuilder>()
  private val fleet = mockk<FleetManager> {
    every { loadThing(THING) } returns flowOf(
      Thing(
        id = THING,
        template = template
      )
    )
  }
  private val taskData = mockk<TaskDataManager> {
    every { observeTasks(THING) } returns flowOf(emptyList())
  }
  private val registry = mockk<TemplateRegistry>()
  private val scopes = mockk<ThingScopeResolver> {
    coEvery { resolveNow(THING) } returns EntityScope.thingChildUnsafe(
      "host",
      THING
    )
  }
  private val sync = mockk<EntitySyncObserver> {
    coEvery {
      awaitSynced(
        any(),
        any(),
        any(),
        any()
      )
    } returns true
  }

  private val attachments = mockk<AttachmentManager>()
  private val jobDocuments = mockk<JobDocumentReleaser>(relaxed = true)

  private val manager = TaskSuggestionManagerImpl(
    client,
    builder,
    SuggestionMapper(),
    fleet,
    taskData,
    registry,
    scopes,
    sync,
    attachments,
    jobDocuments,
    { uid },
  )

  private var uid: String? = "host"

  private val manual = Attachment(
    id = "blob-1",
    name = "POH.pdf",
    mime_type = "application/pdf",
    size_bytes = 2_000_000,
    sha256 = "abc123",
  )

  private val suggestion =
    TaskSuggestion(title = "Replace spark plugs", component_slot_key = "engine")

  private fun job(
    status: AiJobStatus?,
    result: SuggestTasksResult? = null,
    error: AiErrorCode? = null,
    aiSkipped: AiSkipped? = null,
  ) = AiJob(
    id = JOB,
    kind = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
    hostUid = UserId(value_ = "host"),
    thingId = ThingId(value_ = THING),
    status = status,
    stage = "tailoring",
    stageArg = null,
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0),
    result = result?.encodeByteString(),
    error = error,
    aiSkipped = aiSkipped,
  )

  @Test
  fun `asks about the Thing in its host's tree, without documents`() = runTest {
    coEvery {
      client.eligibility(
        any(),
        any(),
        any(),
        any()
      )
    } returns AiEligibility(true, null, false, null)

    manager.eligibility(THING)

    coVerify {
      client.eligibility(
        AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
        ThingId(value_ = THING),
        UserId(value_ = "host"),
        false,
      )
    }
  }

  @Test
  fun `owns the Thing when it is in the caller's own tree`() = runTest {
    assertThat(manager.isOwner(THING)).isTrue()
    uid = "member"
    assertThat(manager.isOwner(THING)).isFalse()
    uid = null
    assertThat(manager.isOwner(THING)).isFalse()
  }

  @Test
  fun `asks with documents when the caller will offer them`() = runTest {
    coEvery { client.eligibility(any(), any(), any(), any()) } returns
      AiEligibility(true, null, true, null)

    manager.eligibility(THING, withDocuments = true)

    coVerify {
      client.eligibility(
        AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
        ThingId(value_ = THING),
        UserId(value_ = "host"),
        true,
      )
    }
  }

  @Test
  fun `waits for each document to upload, then sends its reference`() = runTest {
    val status = MutableStateFlow<AttachmentStatus>(AttachmentStatus.Uploading(0.5f))
    every { attachments.observeStatus("blob-1") } returns status
    coEvery { builder.build(THING, "overview") } returns SuggestTasksRequest()
    val sent = slot<ByteString>()
    coEvery { client.start(any(), capture(sent)) } coAnswers {
      AiStartResult.Started(JOB, joined = false)
    }

    val started = backgroundScope.async { manager.start(THING, "overview", documents = listOf(manual)) }
    testScheduler.runCurrent()
    assertThat(sent.isCaptured).isFalse()

    status.value = AttachmentStatus.Synced
    assertThat(started.await()).isEqualTo(AiStartResult.Started(JOB, joined = false))
    assertThat(SuggestTasksRequest.ADAPTER.decode(sent.captured).documents).containsExactly(
      SourceDocumentRef(
        blob_id = AttachmentId(value_ = "blob-1"),
        name = "POH.pdf",
        mime_type = "application/pdf",
        sha256 = "abc123",
        size_bytes = 2_000_000,
      ),
    )
    coVerify { jobDocuments.record(JOB, THING, listOf(manual)) }
  }

  @Test
  fun `refuses without starting when a document failed to upload`() = runTest {
    every { attachments.observeStatus("blob-1") } returns
      flowOf(AttachmentStatus.Failed(RuntimeException("offline")))

    assertThat(manager.start(THING, "overview", documents = listOf(manual)))
      .isEqualTo(AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null))
    coVerify(exactly = 0) { client.start(any(), any()) }
  }

  @Test
  fun `refuses without starting when a document is still uploading after the wait`() = runTest {
    every { attachments.observeStatus("blob-1") } returns
      MutableStateFlow(AttachmentStatus.Uploading(0.1f))

    assertThat(manager.start(THING, "overview", documents = listOf(manual)))
      .isEqualTo(AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null))
    coVerify(exactly = 0) { client.start(any(), any()) }
  }

  @Test
  fun `one failed upload refuses at once, without waiting on the others`() = runTest {
    every { attachments.observeStatus("blob-1") } returns
      MutableStateFlow(AttachmentStatus.Uploading(0.1f))
    every { attachments.observeStatus("blob-2") } returns
      flowOf(AttachmentStatus.Failed(RuntimeException("offline")))

    val result = manager.start(THING, "overview", documents = listOf(manual, manual.copy(id = "blob-2")))

    assertThat(result).isEqualTo(AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null))
    assertThat(testScheduler.currentTime).isEqualTo(0)
  }

  @Test
  fun `waits for several documents together, within one wait`() = runTest {
    val first = MutableStateFlow<AttachmentStatus>(AttachmentStatus.Uploading(0.1f))
    val second = MutableStateFlow<AttachmentStatus>(AttachmentStatus.Uploading(0.1f))
    every { attachments.observeStatus("blob-1") } returns first
    every { attachments.observeStatus("blob-2") } returns second
    coEvery { builder.build(THING, "overview") } returns SuggestTasksRequest()
    coEvery { client.start(any(), any()) } returns AiStartResult.Started(JOB, joined = false)

    val started = backgroundScope.async {
      manager.start(THING, "overview", documents = listOf(manual, manual.copy(id = "blob-2")))
    }
    testScheduler.advanceTimeBy(90_000)
    first.value = AttachmentStatus.Synced
    testScheduler.advanceTimeBy(20_000)
    second.value = AttachmentStatus.Synced

    // 110 s in all: each took most of the two minutes, but they were waited on side by side.
    assertThat(started.await()).isEqualTo(AiStartResult.Started(JOB, joined = false))
  }

  @Test
  fun `the wait is two minutes for all the documents, not for each`() = runTest {
    val first = MutableStateFlow<AttachmentStatus>(AttachmentStatus.Uploading(0.1f))
    val second = MutableStateFlow<AttachmentStatus>(AttachmentStatus.Uploading(0.1f))
    every { attachments.observeStatus("blob-1") } returns first
    every { attachments.observeStatus("blob-2") } returns second

    val started = backgroundScope.async {
      manager.start(THING, "overview", documents = listOf(manual, manual.copy(id = "blob-2")))
    }
    testScheduler.advanceTimeBy(110_000)
    first.value = AttachmentStatus.Synced
    testScheduler.advanceTimeBy(20_000)
    second.value = AttachmentStatus.Synced

    // Waited one after the other, each would have made it inside its own two minutes.
    assertThat(started.await()).isEqualTo(AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null))
    coVerify(exactly = 0) { client.start(any(), any()) }
  }

  @Test
  fun `a curated-only run neither waits for nor sends documents`() = runTest {
    coEvery { builder.build(THING, "created") } returns SuggestTasksRequest()
    val sent = slot<ByteString>()
    coEvery { client.start(any(), capture(sent)) } returns AiStartResult.Started(JOB, joined = false)

    manager.start(THING, "created", curatedOnly = true, documents = listOf(manual))

    assertThat(SuggestTasksRequest.ADAPTER.decode(sent.captured).documents).isEmpty()
    verify(exactly = 0) { attachments.observeStatus(any()) }
  }

  @Test
  fun `takes a document already in Storage from another device as uploaded`() = runTest {
    every { attachments.observeStatus("blob-1") } returns flowOf(AttachmentStatus.RemoteOnly)
    coEvery { builder.build(THING, "overview") } returns SuggestTasksRequest()
    coEvery { client.start(any(), any()) } returns AiStartResult.Started(JOB, joined = false)

    assertThat(manager.start(THING, "overview", documents = listOf(manual)))
      .isEqualTo(AiStartResult.Started(JOB, joined = false))
  }

  @Test
  fun `waits for the Thing to reach the server, then starts with the built request`() =
    runTest {
      val request = SuggestTasksRequest(
        thing_id = ThingId(value_ = THING),
        entry_point = "overview"
      )
      coEvery { builder.build(THING, "overview") } returns request
      val sent = slot<ByteString>()
      coEvery {
        client.start(
          any(),
          capture(sent)
        )
      } returns AiStartResult.Started(JOB, joined = false)

      val result = manager.start(THING, "overview")

      assertThat(result).isEqualTo(AiStartResult.Started(JOB, joined = false))
      assertThat(SuggestTasksRequest.ADAPTER.decode(sent.captured)).isEqualTo(
        request
      )
      coVerifyOrder {
        sync.awaitSynced(
          CollectionKind.Thing,
          EntityScope.userRoot("host"),
          THING,
          any()
        )
        builder.build(THING, "overview")
        client.start(AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS, any())
      }
    }

  @Test
  fun `asks for the curated suggestions alone, without the logs`() = runTest {
    val request = SuggestTasksRequest(
      thing_id = ThingId(value_ = THING),
      context = SuggestionContext(
        logs = listOf(LogSummary(work_description = "Oil change")),
        logs_truncated = true
      ),
    )
    coEvery { builder.build(THING, "created") } returns request
    val sent = slot<ByteString>()
    coEvery {
      client.start(
        any(),
        capture(sent)
      )
    } returns AiStartResult.Started(JOB, joined = false)

    manager.start(THING, "created", curatedOnly = true)

    val decoded = SuggestTasksRequest.ADAPTER.decode(sent.captured)
    assertThat(decoded.curated_only).isTrue()
    assertThat(decoded.context?.logs).isEmpty()
    assertThat(decoded.context?.logs_truncated).isFalse()
  }

  @Test
  fun `still starts when the Thing is not confirmed synced, and lets the server decide`() =
    runTest {
      coEvery { sync.awaitSynced(any(), any(), any(), any()) } returns false
      coEvery { builder.build(THING, "overview") } returns SuggestTasksRequest()
      coEvery { client.start(any(), any()) } returns AiStartResult.Refused(
        AiErrorCode.NOT_MEMBER,
        null
      )

      assertThat(
        manager.start(
          THING,
          "overview"
        )
      ).isEqualTo(AiStartResult.Refused(AiErrorCode.NOT_MEMBER, null))
    }

  @Test
  fun `turns the latest job into a run state`() = runTest {
    val result = SuggestTasksResult(
      suggestions = listOf(suggestion),
      generation_version = "tasks-4"
    )
    every {
      client.observeLatest(
        AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
        ThingId(value_ = THING)
      )
    } returns flowOf(
      null,
      job(AiJobStatus.AI_JOB_STATUS_QUEUED),
      job(AiJobStatus.AI_JOB_STATUS_RUNNING),
      job(null),
      job(AiJobStatus.AI_JOB_STATUS_SUCCEEDED, result = result),
      job(AiJobStatus.AI_JOB_STATUS_EMPTY),
      job(AiJobStatus.AI_JOB_STATUS_FAILED, error = AiErrorCode.PROVIDER_ERROR),
      job(AiJobStatus.AI_JOB_STATUS_FAILED),
    )

    assertThat(
      manager.observeRun(THING)
        .toList()
    ).containsExactly(
      SuggestionRun.Idle,
      SuggestionRun.Working(JOB, "tailoring", null),
      SuggestionRun.Working(JOB, "tailoring", null),
      // A status this build does not know still reads as working.
      SuggestionRun.Working(JOB, "tailoring", null),
      SuggestionRun.Ready(JOB, result),
      SuggestionRun.Empty(JOB),
      SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR),
      SuggestionRun.Failed(JOB, AiErrorCode.UNKNOWN),
    )
      .inOrder()
  }

  @Test
  fun `carries the curated suggestions in every state, and why the model was skipped`() =
    runTest {
      val curated = SuggestTasksResult(
        suggestions = listOf(TaskSuggestion(title = "Annual")),
        generation_version = "tasks-4"
      )
      val skipped =
        AiSkipped(AiErrorCode.DAILY_LIMIT, Instant.fromEpochMilliseconds(5_000))
      every {
        client.observeLatest(
          AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
          ThingId(value_ = THING)
        )
      } returns flowOf(
        job(AiJobStatus.AI_JOB_STATUS_QUEUED, result = curated),
        job(AiJobStatus.AI_JOB_STATUS_EMPTY, result = curated),
        job(
          AiJobStatus.AI_JOB_STATUS_FAILED,
          result = curated,
          error = AiErrorCode.PROVIDER_ERROR
        ),
        job(
          AiJobStatus.AI_JOB_STATUS_SUCCEEDED,
          result = curated,
          aiSkipped = skipped
        ),
      )

      assertThat(
        manager.observeRun(THING)
          .toList()
      ).containsExactly(
        SuggestionRun.Working(JOB, "tailoring", null, curated),
        SuggestionRun.Empty(JOB, curated),
        SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR, curated),
        SuggestionRun.Ready(JOB, curated, skipped),
      )
        .inOrder()
    }

  @Test
  fun `accepting writes each chosen task, keeps an edit as edited, and closes the run`() =
    runTest {
      val written = mutableListOf<MaintenanceTask>()
      coEvery {
        taskData.addTask(
          THING,
          capture(written)
        )
      } returns Result.success(true)
      val edited = MaintenanceTask(title = "My own wording")
      val run = SuggestionRun.Ready(
        JOB,
        SuggestTasksResult(generation_version = "tasks-4")
      )

      val count = manager.accept(
        THING,
        run,
        listOf(
          AcceptedSuggestion(suggestion),
          AcceptedSuggestion(
            suggestion.copy(title = "Second"),
            edited = edited
          )
        ),
      )

      assertThat(count).isEqualTo(2)
      assertThat(written[0].title).isEqualTo("Replace spark plugs")
      assertThat(written[0].origin?.kind).isEqualTo(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING)
      assertThat(written[0].origin?.generation_version).isEqualTo("tasks-4")
      assertThat(written[1]).isEqualTo(edited)
      coVerifyOrder {
        taskData.addTask(THING, any())
        client.close(JOB)
        // After the writes, so the reference check keeps a document a task now holds.
        jobDocuments.release(JOB)
      }
    }

  @Test
  fun `a failed write drops only its own card`() = runTest {
    coEvery { taskData.addTask(THING, any()) } returnsMany
      listOf(Result.failure(RuntimeException("disk")), Result.success(true))
    val run = SuggestionRun.Ready(JOB, SuggestTasksResult())

    val count = manager.accept(
      THING,
      run,
      listOf(AcceptedSuggestion(suggestion), AcceptedSuggestion(suggestion))
    )

    assertThat(count).isEqualTo(1)
    coVerify(exactly = 1) { client.close(JOB) }
  }

  @Test
  fun `a draft is the suggestion as accepting would write it`() = runTest {
    val draft =
      manager.draftOf(THING, suggestion, generationVersion = "tasks-5")

    assertThat(draft.title).isEqualTo("Replace spark plugs")
    assertThat(draft.origin?.generation_version).isEqualTo("tasks-5")
    assertThat(draft.id).isEmpty() // addTask assigns it
  }

  @Test
  fun `dismissing closes the run and lets go of its documents`() = runTest {
    manager.dismiss(JOB)
    coVerify(exactly = 1) { client.close(JOB) }
    coVerify(exactly = 1) { jobDocuments.release(JOB) }
  }

  private companion object {
    const val THING = "thing-1"
    val JOB = AiJobId("job-1")
  }
}
