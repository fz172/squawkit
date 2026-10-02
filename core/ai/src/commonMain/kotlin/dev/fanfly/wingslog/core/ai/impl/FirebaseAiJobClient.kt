package dev.fanfly.wingslog.core.ai.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiEligibility
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJob
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.firebase.functions.callableDetailsString
import dev.fanfly.wingslog.core.firebase.functions.isCallableClientDefect
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.rpc.aijob.AiJobKind
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * [AiJobClient] on the shared [FirebaseFunctions] and one Firestore listener per observed job.
 *
 * Refusals are read from the callable error's `details.code` (backend `authorizeAiCall`), not from
 * the gRPC status, which cannot tell a daily limit from a spend ceiling. A failure that never
 * reached the server is [AiErrorCode.UNAVAILABLE].
 *
 * Jobs live under the signed-in user (`ai_jobs/{uid}/job/{jobId}`), so the listeners follow the
 * auth state: signed out, there is nothing to observe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAiJobClient(
  private val functions: FirebaseFunctions,
  private val firestore: FirebaseFirestore,
  private val auth: FirebaseAuth,
) : AiJobClient {

  override suspend fun eligibility(
    kind: AiJobKind,
    thingId: ThingId,
    hostUid: UserId,
    withDocuments: Boolean,
  ): AiEligibility = try {
    functions.httpsCallable(GET_ELIGIBILITY)
      .invoke(
        EligibilityRequest(
          kind.value,
          thingId.value_,
          hostUid.value_,
          withDocuments
        )
      )
      .data<EligibilityResponse>()
      .toEligibility()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    report(GET_ELIGIBILITY, e)
    AiEligibility(
      allowed = false,
      reason = failureCode(e),
      documentsAllowed = false,
      nextAvailableAt = null
    )
  }

  override suspend fun start(
    kind: AiJobKind,
    request: ByteString
  ): AiStartResult = try {
    val response = functions.httpsCallable(START)
      .invoke(StartRequest(kind.value, request.base64()))
      .data<StartResponse>()
    AiStartResult.Started(AiJobId(response.jobId), response.joined)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    report(START, e)
    AiStartResult.Refused(
      failureCode(e),
      e.callableDetailsString(NEXT_AVAILABLE_AT)
        .toInstantOrNull()
    )
  }

  override fun observe(jobId: AiJobId): Flow<AiJob?> = signedInUid { uid ->
    jobs(uid).document(jobId.value).snapshots.map { snap -> snap.toAiJobOrNull() }
  }

  override fun observeLatest(kind: AiJobKind, thingId: ThingId): Flow<AiJob?> =
    signedInUid { uid ->
      jobs(uid)
        .where { THING_ID equalTo thingId.value_ }
        .where { KIND equalTo kind.value }
        .orderBy(CREATED_AT, Direction.DESCENDING)
        .limit(1)
        .snapshots
        .map { snap ->
          snap.documents.firstOrNull()
            ?.toAiJobOrNull()
        }
    }

  override suspend fun close(jobId: AiJobId) {
    try {
      functions.httpsCallable(CLOSE)
        .invoke(CloseRequest(jobId.value))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // The job expires within a day regardless, and its input is already gone (§5.8).
      report(CLOSE, e)
    }
  }

  private fun jobs(uid: String) = firestore.collection(AI_JOBS)
    .document(uid)
    .collection(JOB)

  private fun signedInUid(listen: (String) -> Flow<AiJob?>): Flow<AiJob?> =
    auth.authStateChanged.flatMapLatest { user ->
      val uid = user?.uid ?: return@flatMapLatest flowOf(null)
      listen(uid).catch { e ->
        logger.w(e) { "AI job listener failed" }
        emit(null)
      }
    }

  private fun DocumentSnapshot.toAiJobOrNull(): AiJob? {
    if (!exists) return null
    return runCatching {
      data<JobDocFirestore>().toWire()
        .toAiJob(id)
    }
      .onFailure { logger.w(it) { "AI job $id did not decode" } }
      .getOrNull()
  }

  private fun failureCode(e: Exception): AiErrorCode =
    failureCodeOf(
      e.callableDetailsString(CODE),
      (e as? FirebaseFunctionsException)?.code?.name
    )

  private fun report(callable: String, e: Exception) {
    // A refusal with a code is ordinary traffic; only a build the server would not accept, or a
    // request that never left the device, is a defect worth a report (CallableFailure.kt).
    if (e.isCallableClientDefect()) {
      logger.e(e) { "$callable failed on this build" }
    } else {
      logger.w(e) { "$callable refused: ${e.callableDetailsString(CODE)}" }
    }
  }

  private companion object {
    val logger = Logger.withTag("AiJobClient")

    const val GET_ELIGIBILITY = "getAiEligibility"
    const val START = "startAiJob"
    const val CLOSE = "closeAiJob"

    const val AI_JOBS = "ai_jobs"
    const val JOB = "job"
    const val THING_ID = "thingId"
    const val KIND = "kind"
    const val CREATED_AT = "createdAt"

    const val CODE = "code"
    const val NEXT_AVAILABLE_AT = "nextAvailableAt"
  }
}

/** The job document as Firestore holds it; [toWire] turns its Timestamps into milliseconds. */
@Serializable
internal data class JobDocFirestore(
  val kind: Int = 0,
  val hostUid: String = "",
  val thingId: String = "",
  val status: Int = 0,
  val stage: String? = null,
  val stageArg: String? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
  val result: String? = null,
  val error: JobErrorWire? = null,
) {
  fun toWire(): JobDocWire = JobDocWire(
    kind = kind,
    hostUid = hostUid,
    thingId = thingId,
    status = status,
    stage = stage,
    stageArg = stageArg,
    createdAtMillis = createdAt?.toMilliseconds()
      ?.toLong() ?: 0L,
    updatedAtMillis = updatedAt?.toMilliseconds()
      ?.toLong() ?: 0L,
    result = result,
    error = error,
  )
}
