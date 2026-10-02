import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onTaskDispatched } from "firebase-functions/v2/tasks";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { AiJobKind, AiJobStatus } from "../generated/proto/rpc/ai_job/ai_job.js";
import { decideAiAccess, loadAiAccessFacts } from "./authorize.js";
import {
  AI_COST_LOG_COLLECTION,
  AI_COST_LOG_TTL_MS,
  aiJobDocPath,
  aiJobInputDocPath,
  aiSpendDocPath,
  aiUsageDocPath,
  type AiConfig,
  type AiCostLogDoc,
  type AiJobDoc,
  type AiJobErrorDoc,
  type AiJobInputDoc,
  type AiJobRef,
  type AiOwnerTier,
  type AiUsageDoc,
} from "./collections.js";
import { echoPipeline } from "./echoPipeline.js";
import { AiError, type AiErrorCode } from "./errors.js";
import { FirestorePipelineCache } from "./firestoreCache.js";
import { aiJobKindSpec, type AiJobKindSpec } from "./kinds.js";
import type { PipelineCache } from "./tasks/cache.js";
import { createTaskSuggestionPipeline } from "./tasks/taskSuggestionPipeline.js";
import type { PipelineCallRecord } from "./tasks/pipeline.js";

/**
 * The AI worker (docs/ai/task_population_design.md §5.2): one task-queue function that runs any
 * kind of job through the pipeline registered for it.
 *
 * A task queue rather than a Firestore trigger because event functions stop at 540 s, and a
 * three-document run reached 505 s in the bake-off. No retries: a run is not idempotent in cost,
 * and a failed run is free to start again (R21).
 */

// --- Pipelines ----------------------------------------------------------------------------------

export type AiPipelineContext = {
  ref: AiJobRef;
  job: AiJobDoc;
  config: AiConfig;
  ownerTier: AiOwnerTier;
  cache: PipelineCache;
  /** The R19 progress text. Written in order; a failed write never fails the run. */
  reportStage(stage: string, arg?: string): void;
  /** One provider, OCR or cache-hit call, for the cost log and `ai_spend` (§5.6). */
  recordCall(record: PipelineCallRecord): void;
};

export type AiPipelineOutcome = {
  status: "succeeded" | "empty";
  /** The kind's result proto, encoded. */
  result: Uint8Array;
};

export type AiJobFinish = {
  ref: AiJobRef;
  job: AiJobDoc;
  status: AiJobStatus;
  error: AiJobErrorDoc | null;
};

export interface AiPipeline {
  /** Runs the kind's request. Fails by throwing; an AiError carries its §5.7 code. */
  run(request: Uint8Array, context: AiPipelineContext): Promise<AiPipelineOutcome>;
  /** After the job's outcome is written, for every outcome (the R20 push, phase C). */
  onFinished?(finish: AiJobFinish): Promise<void>;
}

const PIPELINES = new Map<AiJobKind, AiPipeline>([
  [AiJobKind.AI_JOB_KIND_ECHO, echoPipeline],
  [AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS, createTaskSuggestionPipeline()],
]);

/** Registers or replaces a kind's pipeline; the built-in kinds are in PIPELINES above. */
export function registerPipeline(kind: AiJobKind, pipeline: AiPipeline): void {
  PIPELINES.set(kind, pipeline);
}

// --- The job ------------------------------------------------------------------------------------

export type AiWorkerDeps = {
  now: () => Date;
  pipelineFor: (kind: AiJobKind) => AiPipeline | undefined;
  cache: PipelineCache;
};

const defaultDeps = (): AiWorkerDeps => ({
  now: () => new Date(),
  pipelineFor: (kind) => PIPELINES.get(kind),
  cache: new FirestorePipelineCache(),
});

/**
 * Runs one job, start to finish:
 *   1. claims it, QUEUED → RUNNING, so a duplicate delivery or a job already failed as stale is a
 *      no-op;
 *   2. re-checks access, since a share can be revoked or the switch turned off after start;
 *   3. runs the pipeline, writing stages, cost records and spend as it goes;
 *   4. writes the outcome, frees the Thing, sets `lastSuccessAt` on a counted success, and deletes
 *      the input (§5.8), all in one transaction;
 *   5. calls the pipeline's `onFinished`.
 *
 * A job closed while running is not an error: its outcome has nowhere to go, but the Thing is
 * still freed and the input still deleted.
 */
export async function handleAiJob(ref: AiJobRef, deps: AiWorkerDeps = defaultDeps()): Promise<void> {
  const jobRef = adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId));
  const inputRef = adminDb.doc(aiJobInputDocPath(ref.callerUid, ref.jobId));

  const job = await claim(ref, deps.now());
  if (job == null) return;
  const spec = aiJobKindSpec(job.kind);
  const usageRef = adminDb.doc(aiUsageDocPath(job.hostUid, job.thingId));

  const pending: Promise<unknown>[] = [];
  let stageWrites: Promise<unknown> = Promise.resolve();
  let finish: { status: AiJobStatus; result: Uint8Array | null; error: AiJobErrorDoc | null };

  try {
    const pipeline = deps.pipelineFor(job.kind);
    const input = (await inputRef.get()).data() as AiJobInputDoc | undefined;
    if (spec == null || pipeline == null) throw new AiError("provider_error", `no pipeline for kind ${job.kind}`);
    if (input == null) throw new AiError("provider_error", "the job's input is gone");
    const request = new Uint8Array(Buffer.from(input.request, "base64"));

    const now = deps.now();
    const target = spec.decodeTarget(request);
    const access = { callerUid: ref.callerUid, hostUid: job.hostUid, thingId: job.thingId, withDocuments: target.documentCount > 0 };
    const facts = await loadAiAccessFacts(access, now);
    // The run itself holds the Thing, and its own day is not used up yet; only who may run, and
    // whether the switch and spend still allow it, are asked again.
    const decision = decideAiAccess(access, { ...facts, usage: null }, now);
    if (!decision.allowed) throw new AiError(decision.code, "access changed after start");

    const outcome = await pipeline.run(request, {
      ref,
      job,
      config: decision.config,
      ownerTier: decision.ownerTier,
      cache: deps.cache,
      reportStage(stage, arg) {
        stageWrites = stageWrites
          .then(() => jobRef.update({ stage, stageArg: arg ?? null, updatedAt: Timestamp.fromDate(deps.now()) }))
          .catch((e: unknown) => logger.warn("runAiJob: stage write failed", { jobId: ref.jobId, error: String(e) }));
      },
      recordCall(record) {
        pending.push(writeCost(record, job, ref, decision.ownerTier, deps.now()));
      },
    });
    finish = {
      status: outcome.status === "succeeded" ? AiJobStatus.AI_JOB_STATUS_SUCCEEDED : AiJobStatus.AI_JOB_STATUS_EMPTY,
      result: outcome.result,
      error: null,
    };
  } catch (e) {
    const code: AiErrorCode = e instanceof AiError ? e.code : "provider_error";
    logger.warn("runAiJob: failed", { jobId: ref.jobId, kind: job.kind, code, detail: e instanceof Error ? e.message : String(e) });
    finish = { status: AiJobStatus.AI_JOB_STATUS_FAILED, result: null, error: { code, detailKey: "" } };
  }

  // Cost first: a record lost to a crash after this point is spend the ceiling never saw.
  await Promise.allSettled(pending);
  await stageWrites;

  const counted = finish.status === AiJobStatus.AI_JOB_STATUS_SUCCEEDED && spec?.countsTowardDailyLimit === true;
  const at = Timestamp.fromDate(deps.now());
  await adminDb.runTransaction(async (tx) => {
    const [jobSnap, usageSnap] = await Promise.all([tx.get(jobRef), tx.get(usageRef)]);
    if (jobSnap.exists) {
      tx.update(jobRef, {
        status: finish.status,
        result: finish.result == null ? null : Buffer.from(finish.result).toString("base64"),
        error: finish.error,
        stage: null,
        stageArg: null,
        updatedAt: at,
      });
    }
    const held = (usageSnap.data() as AiUsageDoc | undefined)?.inFlightJob;
    const usage: Partial<AiUsageDoc> = {};
    if (held?.callerUid === ref.callerUid && held.jobId === ref.jobId) usage.inFlightJob = null;
    if (counted) usage.lastSuccessAt = at;
    if (Object.keys(usage).length > 0) tx.set(usageRef, usage, { merge: true });
    tx.delete(inputRef);
  });

  const pipeline = deps.pipelineFor(job.kind);
  if (pipeline?.onFinished) {
    await pipeline.onFinished({ ref, job, status: finish.status, error: finish.error }).catch((e: unknown) =>
      logger.error("runAiJob: onFinished failed", { jobId: ref.jobId, error: String(e) }),
    );
  }
}

/** QUEUED → RUNNING, or null when there is nothing to run. A gone job's input is deleted here. */
async function claim(ref: AiJobRef, now: Date): Promise<AiJobDoc | null> {
  const jobRef = adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId));
  return adminDb.runTransaction(async (tx) => {
    const job = (await tx.get(jobRef)).data() as AiJobDoc | undefined;
    if (job == null) {
      tx.delete(adminDb.doc(aiJobInputDocPath(ref.callerUid, ref.jobId)));
      return null;
    }
    if (job.status !== AiJobStatus.AI_JOB_STATUS_QUEUED) return null;
    tx.update(jobRef, { status: AiJobStatus.AI_JOB_STATUS_RUNNING, updatedAt: Timestamp.fromDate(now) });
    return { ...job, status: AiJobStatus.AI_JOB_STATUS_RUNNING };
  });
}

/** One cost record and its spend, in one batch so the ceiling sees exactly what the log does. */
async function writeCost(
  record: PipelineCallRecord,
  job: AiJobDoc,
  ref: AiJobRef,
  ownerTier: AiOwnerTier,
  now: Date,
): Promise<void> {
  const doc: AiCostLogDoc = {
    kind: job.kind,
    jobId: ref.jobId,
    stage: record.stage,
    provider: record.provider,
    tier: record.tier,
    ownerTier,
    inputTokens: record.usage.inputTokens,
    outputTokens: record.usage.outputTokens,
    pages: record.pages,
    costMicros: record.usage.costMicros,
    latencyMs: record.latencyMs,
    attempts: record.attempts,
    cacheHit: record.cacheHit,
    createdAt: Timestamp.fromDate(now),
    expiresAt: Timestamp.fromMillis(now.getTime() + AI_COST_LOG_TTL_MS),
  };
  const batch = adminDb.batch();
  batch.set(adminDb.collection(AI_COST_LOG_COLLECTION).doc(), doc);
  if (record.usage.costMicros > 0) {
    batch.set(
      adminDb.doc(aiSpendDocPath(now)),
      {
        [ownerTier === "pro" ? "proMicros" : "freeMicros"]: FieldValue.increment(record.usage.costMicros),
        updatedAt: Timestamp.fromDate(now),
      },
      { merge: true },
    );
  }
  try {
    await batch.commit();
  } catch (e) {
    logger.error("runAiJob: cost write failed", { jobId: ref.jobId, costMicros: record.usage.costMicros, error: String(e) });
  }
}

// --- The function -------------------------------------------------------------------------------

/**
 * Exported as `runAiJob` (AI_WORKER_FUNCTION), the name startAiJob enqueues to. 30 minutes: the
 * longest bake-off run was 505 s, and Gemini's latency swings 2–5x between identical runs.
 */
export const runAiJob = onTaskDispatched<AiJobRef>(
  {
    region: FUNCTION_REGION,
    timeoutSeconds: 1800,
    memory: "2GiB",
    retryConfig: { maxAttempts: 1 },
    rateLimits: { maxConcurrentDispatches: 20 },
  },
  async (request) => {
    const { callerUid, jobId } = (request.data ?? {}) as Partial<AiJobRef>;
    if (typeof callerUid !== "string" || typeof jobId !== "string" || !callerUid || !jobId) {
      logger.error("runAiJob: malformed task", { data: request.data });
      return;
    }
    await handleAiJob({ callerUid, jobId });
  },
);
