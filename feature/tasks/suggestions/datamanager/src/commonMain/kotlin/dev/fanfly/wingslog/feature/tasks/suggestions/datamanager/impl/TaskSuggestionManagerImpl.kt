package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJob
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.CurrentUidProvider
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
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.feature.tasks.suggestions.model.WrittenSuggestion
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
  private val jobDocuments: JobDocumentReleaser,
  private val currentUid: CurrentUidProvider,
  private val clock: Clock = Clock.System,
) : TaskSuggestionManager {

  override suspend fun eligibility(thingId: String, withDocuments: Boolean): AiEligibility =
    client.eligibility(
      KIND,
      ThingId(value_ = thingId),
      UserId(value_ = hostUidOf(thingId)),
      withDocuments = withDocuments
    )

  override suspend fun isOwner(thingId: String): Boolean =
    currentUid.currentUid()?.let { it == hostUidOf(thingId) } ?: false

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
    // A curated-only run reads nothing, and the server refuses one that names documents.
    val sent = if (curatedOnly) emptyList() else documents
    // The worker reads each document from Storage, so it has to be there before the run starts.
    if (!allUploaded(sent)) {
      logger.w { "A document did not reach Storage; not starting" }
      return AiStartResult.Refused(AiErrorCode.DOCUMENT_MISSING, null)
    }
    val built = contextBuilder.build(thingId, entryPoint)
      .copy(documents = sent.map { it.toRef() })
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
    val result = client.start(KIND, request.encodeByteString())
    // Noted on a join too: the run in flight ignores this request, so its documents are let go
    // with that run.
    if (result is AiStartResult.Started) jobDocuments.record(result.jobId, thingId, documents)
    return result
  }

  override fun observeRun(thingId: String): Flow<SuggestionRun> =
    client.observeLatest(KIND, ThingId(value_ = thingId))
      .map { job -> job?.toRun(clock.now()) ?: SuggestionRun.Idle }

  override suspend fun draftOf(
    thingId: String,
    suggestion: TaskSuggestion,
    generationVersion: String,
  ): MaintenanceTask =
    mapper.toTask(suggestion, templateOf(thingId), generationVersion)

  override suspend fun accept(
    thingId: String,
    jobId: AiJobId,
    generationVersion: String,
    chosen: List<AcceptedSuggestion>,
  ): List<WrittenSuggestion> {
    val template = templateOf(thingId)
    val documents = jobDocuments.documentsOf(jobId)
    val written = chosen.mapNotNull { accepted ->
      val task = (
        accepted.edited
          ?: mapper.toTask(accepted.suggestion, template, generationVersion)
        ).withCitedDocument(accepted.suggestion, documents)
        // Named here, so *Undo* knows what to take back.
        .let { if (it.id.isEmpty()) it.copy(id = generateRandomId()) else it }
      WrittenSuggestion(accepted, task.id).takeIf {
        taskDataManager.addTask(thingId, task)
          .onFailure { logger.w(it) { "A suggested task was not written" } }
          .isSuccess
      }
    }
    // Nothing went in: the run and its documents stay, so the same cards can be tried again.
    if (written.isEmpty() && chosen.isNotEmpty()) return written
    client.close(jobId)
    // After the writes, so a document a written task now holds is kept by the reference check.
    jobDocuments.release(jobId)
    return written
  }

  override suspend fun dismiss(jobId: AiJobId) {
    client.close(jobId)
    jobDocuments.release(jobId)
  }

  /**
   * Whether every one of [documents] is in Storage: uploaded, or never on this device at all (a
   * file another device added). Watched together, so the wait is [UPLOAD_WAIT] however many there
   * are, and one failed upload answers at once. False when one failed or any is still going.
   */
  private suspend fun allUploaded(documents: List<Attachment>): Boolean {
    if (documents.isEmpty()) return true
    val statuses = withTimeoutOrNull(UPLOAD_WAIT) {
      combine(documents.map { attachmentManager.observeStatus(it.id) }) { it.toList() }
        .first { all -> all.any { it is AttachmentStatus.Failed } || all.all { it.isSettled() } }
    } ?: return false
    return statuses.all { it == AttachmentStatus.Synced || it == AttachmentStatus.RemoteOnly }
  }

  private suspend fun hostUidOf(thingId: String): String =
    scopeResolver.resolveNow(thingId).hostUid.orEmpty()

  private suspend fun templateOf(thingId: String): ThingTemplate =
    templateRegistry.forThingWithFallback(
      fleetManager.loadThing(thingId)
        .filterNotNull()
        .first()
    )

  private companion object {
    val KIND = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS

    /** Long enough for an ordinary push on a slow connection; the server decides after. */
    val SYNC_WAIT = 20.seconds

    /** A 25 MB manual on a slow connection; the sheet shows the upload meanwhile. */
    val UPLOAD_WAIT = 2.minutes

    /**
     * A run not updated for this long has lost its worker, which is given 30 minutes: the server
     * fails it as stale after 35 (`AI_JOB_STALE_MS`), but only when the next run starts. Longer
     * than the server's, so a device whose clock runs fast does not call a run stale that the
     * server would still join.
     */
    val STALE_AFTER = 40.minutes

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

    /**
     * [task] holding the document [suggestion] cites, as the same blob the run read: one document
     * is stored once however many tasks it yields (PRD R37). This task's copy carries the cited
     * page, so it opens there (R30); each task keeps its own. Unchanged when it cites none, or the
     * document is not this device's to hand on (the run started elsewhere).
     */
    fun MaintenanceTask.withCitedDocument(
      suggestion: TaskSuggestion,
      documents: List<Attachment>,
    ): MaintenanceTask {
      val cited = suggestion.source_document?.value_?.takeIf { it.isNotEmpty() } ?: return this
      val document = documents.firstOrNull { it.id == cited } ?: return this
      val page = suggestion.source_pages.firstOrNull()?.takeIf { it > 0 } ?: 0
      if (attachments.none { it.id == cited }) {
        return copy(attachments = attachments + document.copy(open_page = page))
      }
      // Already held (an edit kept it): it gains the page if it has none of its own.
      return copy(
        attachments = attachments.map {
          if (it.id == cited && it.open_page == 0) it.copy(open_page = page) else it
        },
      )
    }

    fun AiJob.toRun(now: Instant): SuggestionRun {
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
        // tell, with the curated suggestions it started with. One whose worker is long gone will
        // never say otherwise, so it reads as failed, and starting again replaces it.
        else -> if (updatedAt.toEpochMilliseconds() > 0 && now - updatedAt > STALE_AFTER) {
          SuggestionRun.Failed(id, AiErrorCode.STALE, decoded)
        } else {
          SuggestionRun.Working(id, stage, stageArg, decoded)
        }
      }
    }
  }
}
