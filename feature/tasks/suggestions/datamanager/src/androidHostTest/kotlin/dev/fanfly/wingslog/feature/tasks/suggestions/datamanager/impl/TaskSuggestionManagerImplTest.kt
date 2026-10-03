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
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionContextBuilder
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionMapper
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import dev.fanfly.wingslog.rpc.suggesttasks.LastDoneEvidence
import dev.fanfly.wingslog.rpc.suggesttasks.LogSummary
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksRequest
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestionContext
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.ByteString
import org.junit.Test

class TaskSuggestionManagerImplTest {

  private val template = ThingTemplate(id = "airplane", version = 13)
  private val client = mockk<AiJobClient>(relaxed = true)
  private val builder = mockk<SuggestionContextBuilder>()
  private val fleet = mockk<FleetManager> {
    every { loadThing(THING) } returns flowOf(Thing(id = THING, template = template))
  }
  private val taskData = mockk<TaskDataManager> { every { observeTasks(THING) } returns flowOf(emptyList()) }
  private val dueManager = mockk<TaskDueManager>()
  private val registry = mockk<TemplateRegistry>()
  private val scopes = mockk<ThingScopeResolver> {
    coEvery { resolveNow(THING) } returns EntityScope.thingChildUnsafe("host", THING)
  }
  private val sync = mockk<EntitySyncObserver> { coEvery { awaitSynced(any(), any(), any(), any()) } returns true }

  private val manager = TaskSuggestionManagerImpl(
    client, builder, SuggestionMapper(), fleet, taskData, dueManager, registry, scopes, sync,
  )

  private val suggestion = TaskSuggestion(title = "Replace spark plugs", component_slot_key = "engine")

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
    coEvery { client.eligibility(any(), any(), any(), any()) } returns AiEligibility(true, null, false, null)

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
  fun `waits for the Thing to reach the server, then starts with the built request`() = runTest {
    val request = SuggestTasksRequest(thing_id = ThingId(value_ = THING), entry_point = "overview")
    coEvery { builder.build(THING, "overview") } returns request
    val sent = slot<ByteString>()
    coEvery { client.start(any(), capture(sent)) } returns AiStartResult.Started(JOB, joined = false)

    val result = manager.start(THING, "overview")

    assertThat(result).isEqualTo(AiStartResult.Started(JOB, joined = false))
    assertThat(SuggestTasksRequest.ADAPTER.decode(sent.captured)).isEqualTo(request)
    coVerifyOrder {
      sync.awaitSynced(CollectionKind.Thing, EntityScope.userRoot("host"), THING, any())
      builder.build(THING, "overview")
      client.start(AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS, any())
    }
  }

  @Test
  fun `asks for the curated suggestions alone, without the logs`() = runTest {
    val request = SuggestTasksRequest(
      thing_id = ThingId(value_ = THING),
      context = SuggestionContext(logs = listOf(LogSummary(work_description = "Oil change")), logs_truncated = true),
    )
    coEvery { builder.build(THING, "created") } returns request
    val sent = slot<ByteString>()
    coEvery { client.start(any(), capture(sent)) } returns AiStartResult.Started(JOB, joined = false)

    manager.start(THING, "created", curatedOnly = true)

    val decoded = SuggestTasksRequest.ADAPTER.decode(sent.captured)
    assertThat(decoded.curated_only).isTrue()
    assertThat(decoded.context?.logs).isEmpty()
    assertThat(decoded.context?.logs_truncated).isFalse()
  }

  @Test
  fun `still starts when the Thing is not confirmed synced, and lets the server decide`() = runTest {
    coEvery { sync.awaitSynced(any(), any(), any(), any()) } returns false
    coEvery { builder.build(THING, "overview") } returns SuggestTasksRequest()
    coEvery { client.start(any(), any()) } returns AiStartResult.Refused(AiErrorCode.NOT_MEMBER, null)

    assertThat(manager.start(THING, "overview")).isEqualTo(AiStartResult.Refused(AiErrorCode.NOT_MEMBER, null))
  }

  @Test
  fun `turns the latest job into a run state`() = runTest {
    val result = SuggestTasksResult(suggestions = listOf(suggestion), generation_version = "tasks-4")
    every { client.observeLatest(AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS, ThingId(value_ = THING)) } returns flowOf(
      null,
      job(AiJobStatus.AI_JOB_STATUS_QUEUED),
      job(AiJobStatus.AI_JOB_STATUS_RUNNING),
      job(null),
      job(AiJobStatus.AI_JOB_STATUS_SUCCEEDED, result = result),
      job(AiJobStatus.AI_JOB_STATUS_EMPTY),
      job(AiJobStatus.AI_JOB_STATUS_FAILED, error = AiErrorCode.PROVIDER_ERROR),
      job(AiJobStatus.AI_JOB_STATUS_FAILED),
    )

    assertThat(manager.observeRun(THING).toList()).containsExactly(
      SuggestionRun.Idle,
      SuggestionRun.Working(JOB, "tailoring", null),
      SuggestionRun.Working(JOB, "tailoring", null),
      // A status this build does not know still reads as working.
      SuggestionRun.Working(JOB, "tailoring", null),
      SuggestionRun.Ready(JOB, result),
      SuggestionRun.Empty(JOB),
      SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR),
      SuggestionRun.Failed(JOB, AiErrorCode.UNKNOWN),
    ).inOrder()
  }

  @Test
  fun `carries the curated suggestions in every state, and why the model was skipped`() = runTest {
    val curated = SuggestTasksResult(suggestions = listOf(TaskSuggestion(title = "Annual")), generation_version = "tasks-4")
    val skipped = AiSkipped(AiErrorCode.DAILY_LIMIT, Instant.fromEpochMilliseconds(5_000))
    every { client.observeLatest(AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS, ThingId(value_ = THING)) } returns flowOf(
      job(AiJobStatus.AI_JOB_STATUS_QUEUED, result = curated),
      job(AiJobStatus.AI_JOB_STATUS_EMPTY, result = curated),
      job(AiJobStatus.AI_JOB_STATUS_FAILED, result = curated, error = AiErrorCode.PROVIDER_ERROR),
      job(AiJobStatus.AI_JOB_STATUS_SUCCEEDED, result = curated, aiSkipped = skipped),
    )

    assertThat(manager.observeRun(THING).toList()).containsExactly(
      SuggestionRun.Working(JOB, "tailoring", null, curated),
      SuggestionRun.Empty(JOB, curated),
      SuggestionRun.Failed(JOB, AiErrorCode.PROVIDER_ERROR, curated),
      SuggestionRun.Ready(JOB, curated, skipped),
    ).inOrder()
  }

  @Test
  fun `accepting writes each chosen task, keeps an edit as edited, and closes the run`() = runTest {
    val written = mutableListOf<MaintenanceTask>()
    coEvery { taskData.addTask(THING, capture(written)) } returns Result.success(true)
    val edited = MaintenanceTask(title = "My own wording")
    val run = SuggestionRun.Ready(JOB, SuggestTasksResult(generation_version = "tasks-4"))

    val count = manager.accept(
      THING,
      run,
      listOf(AcceptedSuggestion(suggestion), AcceptedSuggestion(suggestion.copy(title = "Second"), edited = edited)),
    )

    assertThat(count).isEqualTo(2)
    assertThat(written[0].title).isEqualTo("Replace spark plugs")
    assertThat(written[0].origin?.kind).isEqualTo(TaskOriginKind.TASK_ORIGIN_KIND_AI_THING)
    assertThat(written[0].origin?.generation_version).isEqualTo("tasks-4")
    assertThat(written[1]).isEqualTo(edited)
    coVerify(exactly = 1) { client.close(JOB) }
  }

  @Test
  fun `a failed write drops only its own card`() = runTest {
    coEvery { taskData.addTask(THING, any()) } returnsMany
      listOf(Result.failure(RuntimeException("disk")), Result.success(true))
    val run = SuggestionRun.Ready(JOB, SuggestTasksResult())

    val count = manager.accept(THING, run, listOf(AcceptedSuggestion(suggestion), AcceptedSuggestion(suggestion)))

    assertThat(count).isEqualTo(1)
    coVerify(exactly = 1) { client.close(JOB) }
  }

  @Test
  fun `previews the first due from the due engine, from now, with no log tied to it`() = runTest {
    val due = DueMetadata(nextDueEngine = 610f, nextDueMeterKey = "engine_hours")
    val mapped = slot<MaintenanceTask>()
    val logsSeen = slot<List<MaintenanceLog>>()
    every { dueManager.computeNextDue(capture(mapped), capture(logsSeen), any()) } returns due
    val done = suggestion.copy(
      last_done = LastDoneEvidence(log_id = MaintenanceLogId(value_ = "log-1"), date = "2026-05-02"),
    )

    assertThat(manager.firstDue(THING, done)).isEqualTo(due)
    assertThat(mapped.captured.force_complied_status).isNull()
    assertThat(logsSeen.captured).isEmpty()
  }

  @Test
  fun `dismissing closes the run`() = runTest {
    manager.dismiss(JOB)
    coVerify(exactly = 1) { client.close(JOB) }
  }

  private companion object {
    const val THING = "thing-1"
    val JOB = AiJobId("job-1")
  }
}
