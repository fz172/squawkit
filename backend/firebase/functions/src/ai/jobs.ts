import { Timestamp, type DocumentReference, type DocumentSnapshot, type Transaction } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { AiJobStatus } from "../generated/proto/rpc/ai_job/ai_job.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { authorizeAiCall, decideAiAccess, loadAiAccessFacts } from "./authorize.js";
import {
  AI_JOB_INPUT_TTL_MS,
  AI_JOB_TTL_MS,
  aiJobDocPath,
  aiJobInputDocPath,
  aiJobsCollectionPath,
  aiUsageDocPath,
  type AiJobDoc,
  type AiJobInputDoc,
  type AiJobRef,
  type AiUsageDoc,
} from "./collections.js";
import type { AiErrorCode } from "./errors.js";
import { aiJobKindSpec, type AiJobKindSpec, type AiJobTarget } from "./kinds.js";

/**
 * The three AI callables (docs/ai/task_population_design.md §5.1). Every kind of AI job goes
 * through them; what differs per kind is in kinds.ts.
 *
 * Each callable is a thin `onCall` over a handler that takes its dependencies, so the tests run the
 * real handler against the emulator with a fake dispatcher.
 */

/** A request proto's cap, decoded: Firestore's 1 MiB document limit with headroom (§5.1). */
export const AI_REQUEST_MAX_BYTES = 512 * 1024;

/**
 * A job still QUEUED or RUNNING this long after its last update is dead: the worker's timeout is
 * 30 minutes, so its instance is gone. Eligibility and start treat it as FAILED (`stale`) and free
 * the Thing (§5.2).
 */
export const AI_JOB_STALE_MS = 35 * 60 * 1000;

/** The worker's export name, which the task queue is addressed by. T09 defines it. */
export const AI_WORKER_FUNCTION = "runAiJob";

/** Hands a new job to the worker. */
export type AiJobDispatcher = (ref: AiJobRef) => Promise<void>;

export const dispatchToWorker: AiJobDispatcher = async (ref) => {
  await getFunctions()
    .taskQueue(`locations/${FUNCTION_REGION}/functions/${AI_WORKER_FUNCTION}`)
    .enqueue(ref, { dispatchDeadlineSeconds: 1800 });
};

// --- getAiEligibility ---------------------------------------------------------------------------

export type AiEligibilityRequest = {
  kind: number;
  thingId: string;
  hostUid: string;
  withDocuments: boolean;
};

export type AiEligibilityResponse = {
  allowed: boolean;
  /** The §5.7 code when not allowed. */
  reason: AiErrorCode | null;
  /** Whether a document run is open to this caller on this Thing (the owner's Pro). */
  documentsAllowed: boolean;
  /** ISO-8601, for `daily_limit` and `spend_ceiling`. */
  nextAvailableAt: string | null;
};

/**
 * What an entry point shows before anything is uploaded (§5.1). Refusals are answers here, not
 * errors: the screen needs the reason either way. A guest is answered `sign_in_required`; only a
 * request that is not from a signed-in app at all is an error.
 *
 * The caller's own run in flight is `allowed`: starting again joins it.
 */
export async function handleGetAiEligibility(
  request: CallableRequest<unknown>,
  now: Date = new Date(),
): Promise<AiEligibilityResponse> {
  const { uid } = requireAuthenticatedApp(request);
  const data = parseEligibilityRequest(request.data);
  if (request.auth?.token?.firebase?.sign_in_provider === "anonymous") {
    return refusal("sign_in_required");
  }

  const access = { callerUid: uid, hostUid: data.hostUid, thingId: data.thingId, withDocuments: data.withDocuments };
  const decision = decideAiAccess(access, await loadAiAccessFacts(access, now), now);
  // The owner's Pro opens documents only where this deploy runs them for the kind.
  const documentsAllowed = decision.documentsAllowed && data.spec.acceptsDocuments;
  if (!decision.allowed) {
    return {
      allowed: false,
      reason: decision.code,
      documentsAllowed,
      nextAvailableAt: decision.nextAvailableAt?.toISOString() ?? null,
    };
  }

  const usage = await adminDb.doc(aiUsageDocPath(data.hostUid, data.thingId)).get();
  const inFlight = await readInFlight(usage.data() as AiUsageDoc | undefined, (ref) => ref.get(), now);
  if (inFlight.state === "active" && inFlight.ref.callerUid !== uid) {
    return { ...refusal("run_in_progress"), documentsAllowed };
  }
  return { allowed: true, reason: null, documentsAllowed, nextAvailableAt: null };
}

function refusal(reason: AiErrorCode): AiEligibilityResponse {
  return { allowed: false, reason, documentsAllowed: false, nextAvailableAt: null };
}

function parseEligibilityRequest(data: unknown): AiEligibilityRequest & { spec: AiJobKindSpec } {
  const d = (data ?? {}) as Record<string, unknown>;
  const spec = requireKind(d.kind);
  return {
    spec,
    kind: d.kind as number,
    thingId: requireId(d.thingId, "thingId"),
    hostUid: requireId(d.hostUid, "hostUid"),
    withDocuments: d.withDocuments === true,
  };
}

export const getAiEligibility = onCall<unknown, Promise<AiEligibilityResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  (request) => handleGetAiEligibility(request),
);

// --- startAiJob ---------------------------------------------------------------------------------

export type StartAiJobResponse = {
  jobId: string;
  /** True when this joined the caller's own run already in flight (§5.1). */
  joined: boolean;
};

/**
 * Starts a run, or joins the caller's own run in flight (§5.1). In one transaction on the Thing's
 * usage document: an active run by the caller is returned, one by anyone else is
 * `run_in_progress` (R19a), a stale one is marked FAILED and replaced. A new run writes the job,
 * carrying the kind's initial result (a task run's curated suggestions), its input and
 * `inFlightJob`, and only then is handed to the worker.
 */
export async function handleStartAiJob(
  request: CallableRequest<unknown>,
  dispatch: AiJobDispatcher,
  now: Date = new Date(),
): Promise<StartAiJobResponse> {
  const { spec, encoded, bytes, target } = parseStartRequest(request.data);
  const { hostUid, thingId } = target;
  const kind = spec.kind;
  if (!hostUid || !thingId) throw new HttpsError("invalid-argument", "The request names no Thing.");

  if (target.documentCount > 0 && !spec.acceptsDocuments) {
    throw new HttpsError("invalid-argument", "This kind of run takes no documents yet.");
  }
  const access = await authorizeAiCall(request, { hostUid, thingId, withDocuments: target.documentCount > 0 }, now);
  if (target.documentCount > access.config.maxDocumentsPerRun) {
    throw new HttpsError("invalid-argument", `At most ${access.config.maxDocumentsPerRun} documents.`);
  }
  const uid = access.callerUid;
  const initial = spec.initialResult(bytes);

  const usageRef = adminDb.doc(aiUsageDocPath(hostUid, thingId));
  const outcome = await adminDb.runTransaction(async (tx) => {
    const usageSnap = await tx.get(usageRef);
    const inFlight = await readInFlight(usageSnap.data() as AiUsageDoc | undefined, (ref) => tx.get(ref), now);
    if (inFlight.state === "active") {
      if (inFlight.ref.callerUid === uid) return { jobId: inFlight.ref.jobId, joined: true };
      throw new HttpsError("failed-precondition", "run_in_progress", { code: "run_in_progress", nextAvailableAt: null });
    }
    if (inFlight.state === "stale") markFailed(tx, inFlight.ref, "stale", now);

    const jobRef = adminDb.collection(aiJobsCollectionPath(uid)).doc();
    const created = Timestamp.fromDate(now);
    const job: AiJobDoc = {
      kind,
      hostUid,
      thingId,
      status: AiJobStatus.AI_JOB_STATUS_QUEUED,
      stage: null,
      stageArg: null,
      createdAt: created,
      updatedAt: created,
      expiresAt: Timestamp.fromMillis(now.getTime() + AI_JOB_TTL_MS),
      // The curated suggestions, before the worker starts (design §6.8).
      result: initial == null ? null : Buffer.from(initial).toString("base64"),
      error: null,
    };
    const input: AiJobInputDoc = {
      kind,
      request: encoded,
      createdAt: created,
      expiresAt: Timestamp.fromMillis(now.getTime() + AI_JOB_INPUT_TTL_MS),
    };
    tx.set(jobRef, job);
    tx.set(adminDb.doc(aiJobInputDocPath(uid, jobRef.id)), input);
    const inFlightJob: AiJobRef = { callerUid: uid, jobId: jobRef.id };
    if (usageSnap.exists) tx.update(usageRef, { inFlightJob });
    else tx.set(usageRef, { lastSuccessAt: null, inFlightJob } satisfies AiUsageDoc);
    return { jobId: jobRef.id, joined: false };
  });

  if (!outcome.joined) {
    const ref = { callerUid: uid, jobId: outcome.jobId };
    try {
      await dispatch(ref);
    } catch (error) {
      // Not left QUEUED for 35 minutes: the Thing is freed now and the caller told now.
      logger.error("startAiJob: dispatch failed", { jobId: ref.jobId, error: String(error) });
      await failAndRelease(usageRef, ref, "provider_error", now);
      throw new HttpsError("unavailable", "provider_error", { code: "provider_error", nextAvailableAt: null });
    }
  }
  return outcome;
}

function parseStartRequest(data: unknown): {
  spec: AiJobKindSpec;
  encoded: string;
  bytes: Uint8Array;
  target: AiJobTarget;
} {
  const d = (data ?? {}) as Record<string, unknown>;
  const spec = requireKind(d.kind);
  if (typeof d.request !== "string" || d.request.length === 0) {
    throw new HttpsError("invalid-argument", "A request is required.");
  }
  const bytes = Buffer.from(d.request, "base64");
  if (bytes.length > AI_REQUEST_MAX_BYTES) {
    throw new HttpsError("invalid-argument", `The request is over ${AI_REQUEST_MAX_BYTES} bytes.`);
  }
  try {
    return { spec, encoded: d.request, bytes, target: spec.decodeTarget(bytes) };
  } catch {
    throw new HttpsError("invalid-argument", "The request does not decode.");
  }
}

export const startAiJob = onCall<unknown, Promise<StartAiJobResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  (request) => handleStartAiJob(request, dispatchToWorker),
);

// --- closeAiJob ---------------------------------------------------------------------------------

/**
 * Deletes the caller's job and its input on accept or dismiss (§5.1). Idempotent, and only ever
 * the caller's own: the path is built from their uid. A subcollection outlives its parent, so the
 * input is deleted explicitly.
 *
 * A job still running is left to the worker, which clears `inFlightJob` when it ends and finds
 * its job gone; clearing it here would let a second run start beside the first.
 */
export async function handleCloseAiJob(request: CallableRequest<unknown>): Promise<{ closed: true }> {
  const { uid } = requireAuthenticatedApp(request);
  const jobId = requireId(((request.data ?? {}) as Record<string, unknown>).jobId, "jobId");
  const batch = adminDb.batch();
  batch.delete(adminDb.doc(aiJobInputDocPath(uid, jobId)));
  batch.delete(adminDb.doc(aiJobDocPath(uid, jobId)));
  await batch.commit();
  return { closed: true };
}

export const closeAiJob = onCall<unknown, Promise<{ closed: true }>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  (request) => handleCloseAiJob(request),
);

// --- shared -------------------------------------------------------------------------------------

type InFlight =
  | { state: "none" }
  | { state: "active"; ref: AiJobRef }
  | { state: "stale"; ref: AiJobRef };

/**
 * The run `inFlightJob` points at, as it stands. A pointer to a job that is gone or finished is
 * "none": the worker clears the pointer when it ends, but a crash, a TTL or a close can get there
 * first.
 */
async function readInFlight(
  usage: AiUsageDoc | undefined,
  get: (ref: DocumentReference) => Promise<DocumentSnapshot>,
  now: Date,
): Promise<InFlight> {
  const ref = usage?.inFlightJob;
  if (!ref) return { state: "none" };
  const snap = await get(adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)));
  const job = snap.data() as AiJobDoc | undefined;
  if (job == null) return { state: "none" };
  if (job.status !== AiJobStatus.AI_JOB_STATUS_QUEUED && job.status !== AiJobStatus.AI_JOB_STATUS_RUNNING) {
    return { state: "none" };
  }
  return now.getTime() - job.updatedAt.toMillis() > AI_JOB_STALE_MS ? { state: "stale", ref } : { state: "active", ref };
}

/** Fails a job that will never run, and deletes its input now rather than at its TTL (§5.8). */
function markFailed(tx: Transaction, ref: AiJobRef, code: AiErrorCode, now: Date): void {
  tx.delete(adminDb.doc(aiJobInputDocPath(ref.callerUid, ref.jobId)));
  tx.update(adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)), {
    status: AiJobStatus.AI_JOB_STATUS_FAILED,
    error: { code, detailKey: "" },
    updatedAt: Timestamp.fromDate(now),
  });
}

/** Fails a job and frees its Thing, if the Thing is still held by that job. */
async function failAndRelease(
  usageRef: DocumentReference,
  ref: AiJobRef,
  code: AiErrorCode,
  now: Date,
): Promise<void> {
  await adminDb.runTransaction(async (tx) => {
    const usage = (await tx.get(usageRef)).data() as AiUsageDoc | undefined;
    markFailed(tx, ref, code, now);
    if (usage?.inFlightJob?.jobId === ref.jobId && usage.inFlightJob.callerUid === ref.callerUid) {
      tx.update(usageRef, { inFlightJob: null });
    }
  });
}

function requireKind(kind: unknown): AiJobKindSpec {
  const spec = aiJobKindSpec(kind);
  if (spec == null) throw new HttpsError("invalid-argument", "Unknown AI job kind.");
  return spec;
}

/** A document id from the caller: non-empty, no path separator, so it cannot reach another path. */
function requireId(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length === 0 || value.includes("/")) {
    throw new HttpsError("invalid-argument", `${name} is required.`);
  }
  return value;
}
