package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJob
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntitySyncObserver
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionContextBuilder
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionMapper
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TaskSuggestionManagerImpl(
  private val client: AiJobClient,
  private val contextBuilder: SuggestionContextBuilder,
  private val mapper: SuggestionMapper,
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val taskDueManager: TaskDueManager,
  private val templateRegistry: TemplateRegistry,
  private val scopeResolver: ThingScopeResolver,
  private val syncObserver: EntitySyncObserver,
) : TaskSuggestionManager {

  override suspend fun eligibility(thingId: String): AiEligibility =
    client.eligibility(KIND, ThingId(value_ = thingId), UserId(value_ = hostUidOf(thingId)), withDocuments = false)

  override suspend fun start(thingId: String, entryPoint: String): AiStartResult {
    val hostUid = hostUidOf(thingId)
    // A Thing made seconds ago on this device may not be on the server yet, and the server refuses
    // one it cannot find as not_member (§5.3). After the wait the server decides either way.
    if (!syncObserver.awaitSynced(CollectionKind.Thing, EntityScope.userRoot(hostUid), thingId, SYNC_WAIT)) {
      logger.w { "Starting suggestions before the Thing is confirmed on the server" }
    }
    val request = contextBuilder.build(thingId, entryPoint)
    return client.start(KIND, request.encodeByteString())
  }

  override fun observeRun(thingId: String): Flow<SuggestionRun> =
    client.observeLatest(KIND, ThingId(value_ = thingId)).map { job -> job?.toRun() ?: SuggestionRun.Idle }

  override suspend fun firstDue(thingId: String, suggestion: TaskSuggestion): DueMetadata {
    val task = mapper.toTask(suggestion, templateOf(thingId), generationVersion = "")
    // No log names a task that does not exist yet, and none is tied to it on accept: the due engine
    // dates it from now. The other tasks are passed for linked rules.
    val tasks = taskDataManager.observeTasks(thingId).first()
    return taskDueManager.computeNextDue(task, logs = emptyList(), allCards = tasks)
  }

  override suspend fun accept(
    thingId: String,
    run: SuggestionRun.Ready,
    chosen: List<AcceptedSuggestion>,
  ): Int {
    val template = templateOf(thingId)
    val written = chosen.count { accepted ->
      val task = accepted.edited
        ?: mapper.toTask(accepted.suggestion, template, run.result.generation_version)
      taskDataManager.addTask(thingId, task)
        .onFailure { logger.w(it) { "A suggested task was not written" } }
        .isSuccess
    }
    client.close(run.jobId)
    return written
  }

  override suspend fun dismiss(jobId: AiJobId) {
    client.close(jobId)
  }

  private suspend fun hostUidOf(thingId: String): String =
    scopeResolver.resolveNow(thingId).segments.getOrNull(1).orEmpty()

  private suspend fun templateOf(thingId: String): ThingTemplate? {
    val thing = fleetManager.loadThing(thingId).filterNotNull().first()
    return thing.template ?: templateRegistry.forThingWithFallback(thing)
  }

  private companion object {
    val KIND = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS

    /** Long enough for an ordinary push on a slow connection; the server decides after. */
    val SYNC_WAIT = 20.seconds

    val logger = Logger.withTag("TaskSuggestionManager")

    fun AiJob.toRun(): SuggestionRun = when (status) {
      AiJobStatus.AI_JOB_STATUS_SUCCEEDED -> {
        val result = result?.let { runCatching { SuggestTasksResult.ADAPTER.decode(it) }.getOrNull() }
        if (result != null) SuggestionRun.Ready(id, result) else SuggestionRun.Failed(id, AiErrorCode.UNKNOWN)
      }
      AiJobStatus.AI_JOB_STATUS_EMPTY -> SuggestionRun.Empty(id)
      AiJobStatus.AI_JOB_STATUS_FAILED -> SuggestionRun.Failed(id, error ?: AiErrorCode.UNKNOWN)
      // QUEUED, RUNNING, and a status this build does not know: still working, as far as it can tell.
      else -> SuggestionRun.Working(id, stage, stageArg)
    }
  }
}
