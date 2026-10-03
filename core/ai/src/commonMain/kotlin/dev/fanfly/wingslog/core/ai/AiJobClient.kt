package dev.fanfly.wingslog.core.ai

import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import okio.ByteString

/**
 * The app's one door to the AI backend (docs/ai/task_population_design.md §7.1): the three
 * callables and the listener on the caller's own job documents. Feature managers never touch
 * Firestore, and a job document is not an entity, so neither they nor the sync engine read it;
 * this does. Shared by every AI feature (#1181, #1183 add kinds, not clients).
 *
 * No caching: a job lives a day at most, and its document is the state.
 */
interface AiJobClient {

  /** What an entry point shows before anything is uploaded. Never throws. */
  suspend fun eligibility(
    kind: AiJobKind,
    thingId: ThingId,
    hostUid: UserId,
    withDocuments: Boolean,
  ): AiEligibility

  /** Starts a run of [kind] with its encoded request proto, or joins the caller's own in flight. */
  suspend fun start(kind: AiJobKind, request: ByteString): AiStartResult

  /** The job as it changes; null once it is closed or expired, or while signed out. */
  fun observe(jobId: AiJobId): Flow<AiJob?>

  /** The caller's newest job of [kind] on [thingId], so a returning user finds their run. */
  fun observeLatest(kind: AiJobKind, thingId: ThingId): Flow<AiJob?>

  /** Deletes the job on accept or dismiss. Idempotent; a failure is logged, not thrown. */
  suspend fun close(jobId: AiJobId)
}

/** The backend's id for one job. Only meaningful to the user who started it. */
@JvmInline
value class AiJobId(val value: String)

data class AiJob(
  val id: AiJobId,
  val kind: AiJobKind?,
  val hostUid: UserId,
  val thingId: ThingId,
  /** Null for a status this build does not know, which a caller treats as still working. */
  val status: AiJobStatus?,
  /** The progress text's key, as the kind's pipeline names it ("reading_document", …). */
  val stage: String?,
  /** The stage's argument, such as the document being read. */
  val stageArg: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
  /**
   * The kind's result proto, encoded, on SUCCEEDED and EMPTY. A task job carries its curated
   * suggestions here from the start, and keeps them when it fails (design §6.8).
   */
  val result: ByteString?,
  val error: AiErrorCode?,
  /** Set when the job returned its curated suggestions alone because the model was refused. */
  val aiSkipped: AiSkipped? = null,
)

/**
 * Why a job's model did not run, though the job succeeded with its curated suggestions (design
 * §5.1): [AiErrorCode.DISABLED], [AiErrorCode.DAILY_LIMIT] or [AiErrorCode.SPEND_CEILING].
 */
data class AiSkipped(
  val reason: AiErrorCode,
  /** When the model can run again, for the daily limit and the spending ceiling. */
  val nextAvailableAt: Instant?,
)

data class AiEligibility(
  val allowed: Boolean,
  /** Why not, when not allowed. */
  val reason: AiErrorCode?,
  /** Whether a document run is open here: the Thing owner's Pro, never the caller's. */
  val documentsAllowed: Boolean,
  /** When asking again can succeed, for [AiErrorCode.DAILY_LIMIT] and [AiErrorCode.SPEND_CEILING]. */
  val nextAvailableAt: Instant?,
)

sealed interface AiStartResult {
  /** [joined] when this is the caller's own run already in flight. */
  data class Started(val jobId: AiJobId, val joined: Boolean) : AiStartResult

  /** The server said no, and why. */
  data class Refused(val reason: AiErrorCode, val nextAvailableAt: Instant?) :
    AiStartResult
}
