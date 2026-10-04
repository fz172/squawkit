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
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionContextBuilder
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionMapper
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.id.AttachmentId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import dev.fanfly.wingslog.rpc.suggesttasks.SourceDocumentRef
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TaskSuggestionManagerImpl(
  private val client: AiJobClient,
  private val contextBuilder: SuggestionContextBuilder,
  private val mapper: SuggestionMapper,
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val templateRegistry: TemplateRegistry,
  private val scopeResolver: ThingScopeResolver,
  private val syncObserver: EntitySyncObserver,
  private val attachmentManager: AttachmentManager,
) : TaskSuggestionManager {

  override suspend fun eligibility(thingId: String, withDocuments: Boolean): AiEligibility =
    client.eligibility(
      KIND,
      ThingId(value_ = thingId),
      UserId(value_ = hostUidOf(thingId)),
      withDocuments = withDocuments
    )

  override suspend fun start(
    thingId: String,
    entryPoint: String,
    curatedOnly: Boolean,
    documents: List<Attachment>,
  ): AiStartResult {
    val hostUid = hostUidOf(thingId)
    // A Thing made seconds ago on this device may not be on the server yet, and the server refuses
    // one it cannot find as not_member (§5.3). After the wait the server decides either way.
    if (!syncObserver.awaitSynced(
        CollectionKind.Thing,
        EntityScope.userRoot(hostUid),
        thingId,
        SYNC_WAIT
      )
    ) {
      logger.w { "Starting suggestions before the Thing is confirmed on the server" }
    }
    // The worker reads each document from Storage, so it has to be there before the run starts.
    val notUploaded = documents.firstOrNull { !awaitUploaded(it) }
    if (notUploaded != null) {
      logger.w { "A document did not reach Storage; not starting" }
      return AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null)
    }
    val built = contextBuilder.build(thingId, entryPoint)
      .copy(documents = documents.map { it.toRef() })
    // The curated list is fitted to the Thing's slots, meters and tasks (§6.8), never its logs.
    val request = if (curatedOnly) {
      built.copy(
        curated_only = true,
        context = built.context?.copy(
          logs = emptyList(),
          logs_truncated = false
        )
      )
    } else {
      built
    }
    return client.start(KIND, request.encodeByteString())
  }

  override fun observeRun(thingId: String): Flow<SuggestionRun> =
    client.observeLatest(KIND, ThingId(value_ = thingId))
      .map { job -> job?.toRun() ?: SuggestionRun.Idle }

  override suspend fun draftOf(
    thingId: String,
    suggestion: TaskSuggestion,
    generationVersion: String,
  ): MaintenanceTask =
    mapper.toTask(suggestion, templateOf(thingId), generationVersion)

  override suspend fun accept(
    thingId: String,
    run: SuggestionRun.Ready,
    chosen: List<AcceptedSuggestion>,
  ): Int {
    val template = templateOf(thingId)
    val written = chosen.count { accepted ->
      val task = accepted.edited
        ?: mapper.toTask(
          accepted.suggestion,
          template,
          run.result.generation_version
        )
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

  /**
   * Whether [document]'s bytes are in Storage: uploaded, or never on this device at all (a file
   * another device added). False when its upload failed or is still going after [UPLOAD_WAIT].
   */
  private suspend fun awaitUploaded(document: Attachment): Boolean {
    val settled = withTimeoutOrNull(UPLOAD_WAIT) {
      attachmentManager.observeStatus(document.id)
        .filter { it.isSettled() }
        .first()
    }
    return settled == AttachmentStatus.Synced || settled == AttachmentStatus.RemoteOnly
  }

  private suspend fun hostUidOf(thingId: String): String =
    scopeResolver.resolveNow(thingId).segments.getOrNull(1)
      .orEmpty()

  private suspend fun templateOf(thingId: String): ThingTemplate? {
    val thing = fleetManager.loadThing(thingId)
      .filterNotNull()
      .first()
    return thing.template ?: templateRegistry.forThingWithFallback(thing)
  }

  private companion object {
    val KIND = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS

    /** Long enough for an ordinary push on a slow connection; the server decides after. */
    val SYNC_WAIT = 20.seconds

    /** A 25 MB manual on a slow connection; the sheet shows the upload meanwhile. */
    val UPLOAD_WAIT = 2.minutes

    val logger = Logger.withTag("TaskSuggestionManager")

    fun AttachmentStatus.isSettled(): Boolean =
      this == AttachmentStatus.Synced ||
        this == AttachmentStatus.RemoteOnly ||
        this is AttachmentStatus.Failed

    fun Attachment.toRef() = SourceDocumentRef(
      blob_id = AttachmentId(value_ = id),
      name = name,
      mime_type = mime_type,
      sha256 = sha256,
      size_bytes = size_bytes,
    )

    fun AiJob.toRun(): SuggestionRun {
      val decoded =
        result?.let { runCatching { SuggestTasksResult.ADAPTER.decode(it) }.getOrNull() }
      return when (status) {
        AiJobStatus.AI_JOB_STATUS_SUCCEEDED ->
          if (decoded != null) {
            SuggestionRun.Ready(id, decoded, aiSkipped)
          } else {
            SuggestionRun.Failed(id, AiErrorCode.UNKNOWN)
          }

        AiJobStatus.AI_JOB_STATUS_EMPTY -> SuggestionRun.Empty(id, decoded)
        AiJobStatus.AI_JOB_STATUS_FAILED -> SuggestionRun.Failed(
          id,
          error ?: AiErrorCode.UNKNOWN,
          decoded
        )
        // QUEUED, RUNNING, and a status this build does not know: still working, as far as it can
        // tell, with the curated suggestions it started with.
        else -> SuggestionRun.Working(id, stage, stageArg, decoded)
      }
    }
  }
}
