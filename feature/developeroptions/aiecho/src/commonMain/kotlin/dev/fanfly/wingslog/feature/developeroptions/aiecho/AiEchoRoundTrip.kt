package dev.fanfly.wingslog.feature.developeroptions.aiecho

import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.fanfly.wingslog.rpc.aijob.AiJobStatus
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString

/**
 * One echo job, end to end (AI_JOB_KIND_ECHO; phase A's exit, docs/ai/task_population_design.md
 * §15): start it on a Thing the user can reach, follow its document to a terminal status, check the
 * result is the request byte for byte, and close it. Every leg is the production path; only the
 * pipeline differs, so a pass proves the callables, the queue, the worker, the rules on the job
 * document and this host's listener.
 *
 * The server still applies every gate, the kill switch included, so a refusal here is a finding too.
 */
class AiEchoRoundTrip(
  private val client: AiJobClient,
  private val fleet: FleetManager,
  private val scopes: ThingScopeResolver,
  private val timeSource: TimeSource = TimeSource.Monotonic,
) {

  suspend fun run(onStep: (EchoStep) -> Unit): EchoOutcome {
    val thingId = fleet.observeFleetDashboard().first().firstOrNull()?.thing?.id
      ?: return EchoOutcome.NoThing
    // A shared Thing lives in its host's tree: users/{hostUid}/thing/{thingId}.
    val hostUid = scopes.resolveNow(thingId).hostUid ?: return EchoOutcome.NoThing
    val request = SuggestTasksRequest(
      thing_id = ThingId(value_ = thingId),
      host_uid = UserId(value_ = hostUid),
      entry_point = ENTRY_POINT,
    ).encodeByteString()

    val started = timeSource.markNow()
    onStep(EchoStep.Starting)
    val jobId = when (val start = client.start(AiJobKind.AI_JOB_KIND_ECHO, request)) {
      is AiStartResult.Refused -> return EchoOutcome.Refused(start.reason)
      is AiStartResult.Started -> start.jobId
    }
    try {
      val job = withTimeoutOrNull(TIMEOUT) {
        client.observe(jobId).first { job ->
          job?.let { onStep(EchoStep.Running(it.status, it.stage)) }
          job?.status in TERMINAL
        }
      } ?: return EchoOutcome.TimedOut
      return when (job.status) {
        AiJobStatus.AI_JOB_STATUS_SUCCEEDED ->
          if (job.result == request) EchoOutcome.Passed(started.elapsedNow()) else EchoOutcome.Mismatch(job.result)
        else -> EchoOutcome.Failed(job.error ?: AiErrorCode.UNKNOWN)
      }
    } finally {
      onStep(EchoStep.Closing)
      client.close(jobId)
    }
  }

  private companion object {
    /** Analytics only; marks the run as this tool's. */
    const val ENTRY_POINT = "developer_echo"

    /** Generous: the queue's first dispatch after a deploy can take a while to warm up. */
    val TIMEOUT = 2.minutes

    val TERMINAL = setOf(
      AiJobStatus.AI_JOB_STATUS_SUCCEEDED,
      AiJobStatus.AI_JOB_STATUS_EMPTY,
      AiJobStatus.AI_JOB_STATUS_FAILED,
    )
  }
}

sealed interface EchoStep {
  data object Starting : EchoStep

  /** [status] null is one this build does not know. */
  data class Running(val status: AiJobStatus?, val stage: String?) : EchoStep

  data object Closing : EchoStep
}

sealed interface EchoOutcome {
  data class Passed(val elapsed: Duration) : EchoOutcome

  /** The server refused to start it; for example `DISABLED` while the kill switch is off. */
  data class Refused(val reason: AiErrorCode) : EchoOutcome

  /** The worker ran it and failed. */
  data class Failed(val reason: AiErrorCode) : EchoOutcome

  /** SUCCEEDED, but not with the bytes sent. */
  data class Mismatch(val result: ByteString?) : EchoOutcome

  /** No terminal status within the timeout: the worker never ran, or the listener never heard. */
  data object TimedOut : EchoOutcome

  /** No Thing in the fleet to run it on. */
  data object NoThing : EchoOutcome
}
