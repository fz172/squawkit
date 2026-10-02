package dev.fanfly.wingslog.core.ai.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import kotlin.time.Instant
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import okio.ByteString.Companion.toByteString
import org.junit.Test

class AiWireTest {

  @Test
  fun `every callable wire type has a generated serializer with the backend's field names`() {
    // A descriptor that knows its elements is a generated one; without the serialization plugin
    // these would fail at the call, not at build time.
    assertThat(serializer<EligibilityRequest>().descriptor.elementNames.toList())
      .containsExactly("kind", "thingId", "hostUid", "withDocuments")
    assertThat(serializer<EligibilityResponse>().descriptor.elementNames.toList())
      .containsExactly(
        "allowed",
        "reason",
        "documentsAllowed",
        "nextAvailableAt"
      )
    assertThat(serializer<StartRequest>().descriptor.elementNames.toList()).containsExactly(
      "kind",
      "request"
    )
    assertThat(serializer<StartResponse>().descriptor.elementNames.toList()).containsExactly(
      "jobId",
      "joined"
    )
    assertThat(serializer<CloseRequest>().descriptor.elementNames.toList()).containsExactly(
      "jobId"
    )
    assertThat(serializer<JobDocFirestore>().descriptor.elementNames.toList()).containsExactly(
      "kind",
      "hostUid",
      "thingId",
      "status",
      "stage",
      "stageArg",
      "createdAt",
      "updatedAt",
      "result",
      "error",
    )
  }

  @Test
  fun `every backend code maps both ways, and an unknown one reads as UNKNOWN`() {
    val backend = listOf(
      "sign_in_required",
      "disabled",
      "not_member",
      "owner_not_pro",
      "daily_limit",
      "run_in_progress",
      "spend_ceiling",
      "document_missing",
      "document_too_large",
      "document_unreadable",
      "no_schedule_found",
      "provider_error",
      "invalid_output",
      "stale",
    )
    assertThat(backend.map { AiErrorCode.fromWire(it).wire }).isEqualTo(backend)
    assertThat(AiErrorCode.fromWire("a_code_from_the_future")).isEqualTo(
      AiErrorCode.UNKNOWN
    )
    assertThat(AiErrorCode.fromWire(null)).isEqualTo(AiErrorCode.UNKNOWN)
  }

  @Test
  fun `a refusal reads its code from details, and a failure without one by status`() {
    assertThat(failureCodeOf("daily_limit", "RESOURCE_EXHAUSTED")).isEqualTo(
      AiErrorCode.DAILY_LIMIT
    )
    assertThat(failureCodeOf("spend_ceiling", "RESOURCE_EXHAUSTED")).isEqualTo(
      AiErrorCode.SPEND_CEILING
    )
    assertThat(failureCodeOf(null, null)).isEqualTo(AiErrorCode.UNAVAILABLE)
    assertThat(
      failureCodeOf(
        null,
        "INTERNAL"
      )
    ).isEqualTo(AiErrorCode.UNAVAILABLE)
    assertThat(
      failureCodeOf(
        null,
        "UNAUTHENTICATED"
      )
    ).isEqualTo(AiErrorCode.APP_UNVERIFIED)
    assertThat(
      failureCodeOf(
        null,
        "INVALID_ARGUMENT"
      )
    ).isEqualTo(AiErrorCode.UNKNOWN)
  }

  @Test
  fun `eligibility keeps the reason only when refused, and parses the time`() {
    val refused = EligibilityResponse(
      allowed = false,
      reason = "daily_limit",
      documentsAllowed = true,
      nextAvailableAt = "2026-10-16T11:00:00.000Z",
    ).toEligibility()
    assertThat(refused.reason).isEqualTo(AiErrorCode.DAILY_LIMIT)
    assertThat(refused.documentsAllowed).isTrue()
    assertThat(refused.nextAvailableAt).isEqualTo(Instant.parse("2026-10-16T11:00:00Z"))

    val allowed = EligibilityResponse(
      allowed = true,
      reason = "ignored",
      nextAvailableAt = "not a time"
    )
      .toEligibility()
    assertThat(allowed.reason).isNull()
    assertThat(allowed.nextAvailableAt).isNull()
  }

  @Test
  fun `a job document maps to an AiJob`() {
    val result = byteArrayOf(1, 2, 3).toByteString()
    val job = JobDocWire(
      kind = AiJobKind.AI_JOB_KIND_ECHO.value,
      hostUid = "host",
      thingId = "thing",
      status = AiJobStatus.AI_JOB_STATUS_SUCCEEDED.value,
      stage = "echoing",
      stageArg = null,
      createdAtMillis = 1_000,
      updatedAtMillis = 2_000,
      result = result.base64(),
      error = null,
    ).toAiJob("j1")

    assertThat(job.id).isEqualTo(AiJobId("j1"))
    assertThat(job.kind).isEqualTo(AiJobKind.AI_JOB_KIND_ECHO)
    assertThat(job.hostUid).isEqualTo(UserId(value_ = "host"))
    assertThat(job.thingId).isEqualTo(ThingId(value_ = "thing"))
    assertThat(job.status).isEqualTo(AiJobStatus.AI_JOB_STATUS_SUCCEEDED)
    assertThat(job.createdAt).isEqualTo(Instant.fromEpochMilliseconds(1_000))
    assertThat(job.result).isEqualTo(result)
    assertThat(job.error).isNull()
  }

  @Test
  fun `a failed job carries its code, and an unknown status or kind reads as null`() {
    val job = JobDocWire(
      status = 77,
      kind = 55,
      error = JobErrorWire(code = "stale")
    ).toAiJob("j2")
    assertThat(job.status).isNull()
    assertThat(job.kind).isNull()
    assertThat(job.error).isEqualTo(AiErrorCode.STALE)
    assertThat(job.result).isNull()
  }
}
