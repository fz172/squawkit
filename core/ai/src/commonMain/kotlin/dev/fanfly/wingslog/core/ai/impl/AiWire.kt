package dev.fanfly.wingslog.core.ai.impl

import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJob
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.decodeBase64

/**
 * The wire shapes of the AI callables and the job document (backend `src/ai/jobs.ts` and
 * `collections.ts`). Every field defaults, so a missing key decodes rather than failing, as the
 * other callable clients do. `internal` so the tests can prove a serializer exists for each.
 */

@Serializable
internal data class EligibilityRequest(
  val kind: Int,
  val thingId: String,
  val hostUid: String,
  val withDocuments: Boolean,
)

@Serializable
internal data class EligibilityResponse(
  val allowed: Boolean = false,
  val reason: String? = null,
  val documentsAllowed: Boolean = false,
  val nextAvailableAt: String? = null,
)

@Serializable
internal data class StartRequest(val kind: Int, val request: String)

@Serializable
internal data class StartResponse(
  val jobId: String = "",
  val joined: Boolean = false
)

@Serializable
internal data class CloseRequest(val jobId: String)

@Serializable
internal data class JobErrorWire(
  val code: String = "",
  val detailKey: String = ""
)

/**
 * `ai_jobs/{uid}/job/{jobId}`, minus `expiresAt` (TTL's business). Times are epoch milliseconds
 * here; the Firestore layer converts its Timestamps before building this, so the mapping below
 * stays plain and testable.
 */
internal data class JobDocWire(
  val kind: Int = 0,
  val hostUid: String = "",
  val thingId: String = "",
  val status: Int = 0,
  val stage: String? = null,
  val stageArg: String? = null,
  val createdAtMillis: Long = 0,
  val updatedAtMillis: Long = 0,
  val result: String? = null,
  val error: JobErrorWire? = null,
  val aiSkipped: AiSkippedWire? = null,
)

/** The job's `aiSkipped`, with its time in epoch milliseconds as [JobDocWire]'s are. */
internal data class AiSkippedWire(
  val code: String = "",
  val nextAvailableAtMillis: Long? = null,
)

internal fun EligibilityResponse.toEligibility(): AiEligibility = AiEligibility(
  allowed = allowed,
  reason = if (allowed) null else AiErrorCode.fromWire(reason),
  documentsAllowed = documentsAllowed,
  nextAvailableAt = nextAvailableAt.toInstantOrNull(),
)

internal fun JobDocWire.toAiJob(id: String): AiJob = AiJob(
  id = AiJobId(id),
  kind = AiJobKind.fromValue(kind),
  hostUid = UserId(value_ = hostUid),
  thingId = ThingId(value_ = thingId),
  status = AiJobStatus.fromValue(status),
  stage = stage,
  stageArg = stageArg,
  createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
  updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
  result = result?.decodeBase64(),
  error = error?.let { AiErrorCode.fromWire(it.code) },
  aiSkipped = aiSkipped?.let { skipped ->
    AiSkipped(
      reason = AiErrorCode.fromWire(skipped.code),
      nextAvailableAt = skipped.nextAvailableAtMillis?.let(Instant::fromEpochMilliseconds),
    )
  },
)

internal fun String?.toInstantOrNull(): Instant? =
  this?.let { runCatching { Instant.parse(it) }.getOrNull() }

/**
 * Why a callable failed. The backend's `details.code` when it sent one. Otherwise, by status name
 * (a String, as in CallableFailure.kt, so this is testable off-device): no status at all, or a
 * network-shaped one, is [AiErrorCode.UNAVAILABLE]; App Check's UNAUTHENTICATED is
 * [AiErrorCode.APP_UNVERIFIED]; any other answer without a code is [AiErrorCode.UNKNOWN]. The
 * Android SDK folds network failures into INTERNAL.
 */
internal fun failureCodeOf(
  detailsCode: String?,
  statusName: String?
): AiErrorCode = when {
  detailsCode != null -> AiErrorCode.fromWire(detailsCode)
  statusName == null || statusName in NO_ANSWER_STATUSES -> AiErrorCode.UNAVAILABLE
  // A signed-in client only meets UNAUTHENTICATED from App Check: a guest gets a code.
  statusName == UNAUTHENTICATED -> AiErrorCode.APP_UNVERIFIED
  else -> AiErrorCode.UNKNOWN
}

private val NO_ANSWER_STATUSES =
  setOf("UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL")

private const val UNAUTHENTICATED = "UNAUTHENTICATED"
