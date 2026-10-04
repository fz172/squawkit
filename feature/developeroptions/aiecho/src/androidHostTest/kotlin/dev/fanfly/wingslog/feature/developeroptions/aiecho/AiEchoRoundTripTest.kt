package dev.fanfly.wingslog.feature.developeroptions.aiecho

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJob
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksRequest
import dev.fanfly.wingslog.thing.Thing
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test

class AiEchoRoundTripTest {

  private val fleet = mockk<FleetManager> {
    every { observeFleetDashboard() } returns flowOf(
      listOf(FleetEntry(thing = Thing(id = "t1"), shared = true, role = ShareRole.SHARE_ROLE_TECHNICIAN)),
    )
  }
  private val scopes = mockk<ThingScopeResolver> {
    coEvery { resolveNow("t1") } returns EntityScope.thingChildUnsafe("host", "t1")
  }

  /** Answers start with [startResult], then plays [statuses] on the job's document. */
  private class FakeClient(
    private val startResult: AiStartResult,
    private val finish: (sent: ByteString) -> AiJob?,
  ) : AiJobClient {
    var sent: ByteString? = null
    var sentKind: AiJobKind? = null
    val closed = mutableListOf<AiJobId>()
    private val job = MutableStateFlow<AiJob?>(null)

    override suspend fun eligibility(kind: AiJobKind, thingId: ThingId, hostUid: UserId, withDocuments: Boolean) =
      AiEligibility(allowed = true, reason = null, documentsAllowed = false, nextAvailableAt = null)

    override suspend fun start(kind: AiJobKind, request: ByteString): AiStartResult {
      sent = request
      sentKind = kind
      job.value = finish(request)
      return startResult
    }

    override fun observe(jobId: AiJobId): Flow<AiJob?> = job

    override fun observeLatest(kind: AiJobKind, thingId: ThingId): Flow<AiJob?> = job

    override suspend fun isGone(jobId: AiJobId) = false

    override suspend fun close(jobId: AiJobId) {
      closed += jobId
    }
  }

  private fun job(status: AiJobStatus, result: ByteString? = null, error: AiErrorCode? = null) = AiJob(
    id = AiJobId("j1"),
    kind = AiJobKind.AI_JOB_KIND_ECHO,
    hostUid = UserId(value_ = "host"),
    thingId = ThingId(value_ = "t1"),
    status = status,
    stage = null,
    stageArg = null,
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0),
    result = result,
    error = error,
  )

  private val started = AiStartResult.Started(AiJobId("j1"), joined = false)

  @Test
  fun `passes when the job succeeds with the request's own bytes, and closes it`() = runTest {
    val client = FakeClient(started) { sent -> job(AiJobStatus.AI_JOB_STATUS_SUCCEEDED, result = sent) }
    val steps = mutableListOf<EchoStep>()

    val outcome = AiEchoRoundTrip(client, fleet, scopes).run { steps += it }

    assertThat(outcome).isInstanceOf(EchoOutcome.Passed::class.java)
    assertThat(client.sentKind).isEqualTo(AiJobKind.AI_JOB_KIND_ECHO)
    val request = SuggestTasksRequest.ADAPTER.decode(client.sent!!)
    assertThat(request.thing_id?.value_).isEqualTo("t1")
    assertThat(request.host_uid?.value_).isEqualTo("host")
    assertThat(steps.first()).isEqualTo(EchoStep.Starting)
    assertThat(steps.last()).isEqualTo(EchoStep.Closing)
    assertThat(client.closed).containsExactly(AiJobId("j1"))
  }

  @Test
  fun `reports a refusal without closing anything`() = runTest {
    val client = FakeClient(AiStartResult.Refused(AiErrorCode.DISABLED, null)) { null }

    assertThat(AiEchoRoundTrip(client, fleet, scopes).run {}).isEqualTo(EchoOutcome.Refused(AiErrorCode.DISABLED))
    assertThat(client.closed).isEmpty()
  }

  @Test
  fun `reports the worker's failure code, and closes the job`() = runTest {
    val client = FakeClient(started) { job(AiJobStatus.AI_JOB_STATUS_FAILED, error = AiErrorCode.NOT_MEMBER) }

    assertThat(AiEchoRoundTrip(client, fleet, scopes).run {}).isEqualTo(EchoOutcome.Failed(AiErrorCode.NOT_MEMBER))
    assertThat(client.closed).containsExactly(AiJobId("j1"))
  }

  @Test
  fun `catches a result that came back changed`() = runTest {
    val changed = "not the request".encodeUtf8()
    val client = FakeClient(started) { job(AiJobStatus.AI_JOB_STATUS_SUCCEEDED, result = changed) }

    assertThat(AiEchoRoundTrip(client, fleet, scopes).run {}).isEqualTo(EchoOutcome.Mismatch(changed))
  }

  @Test
  fun `times out when the job never finishes, and still closes it`() = runTest {
    val client = FakeClient(started) { job(AiJobStatus.AI_JOB_STATUS_QUEUED) }

    assertThat(AiEchoRoundTrip(client, fleet, scopes).run {}).isEqualTo(EchoOutcome.TimedOut)
    assertThat(client.closed).containsExactly(AiJobId("j1"))
  }

  @Test
  fun `needs a Thing to run on`() = runTest {
    every { fleet.observeFleetDashboard() } returns flowOf(emptyList())
    val client = FakeClient(started) { null }

    assertThat(AiEchoRoundTrip(client, fleet, scopes).run {}).isEqualTo(EchoOutcome.NoThing)
    assertThat(client.sent).isNull()
  }
}
