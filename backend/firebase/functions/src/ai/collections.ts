import type { Timestamp } from "firebase-admin/firestore";

import type { AiJobKind, AiJobStatus } from "../generated/proto/rpc/ai_job/ai_job.js";
import type { AiErrorCode } from "./errors.js";
import type { PipelineCallRecord, PipelineStage } from "./tasks/pipeline.js";

/**
 * The AI backend's Firestore collections (docs/ai/task_population_design.md §4.3). None is an
 * entity: the sync engine never reads them, and only `ai_jobs` is visible to a client at all, read
 * by `core/ai` (design §7.1). Names, paths and field shapes live here so the callables, the worker,
 * the seed script and the rules suite agree. `firestore.rules` and `firestore.indexes.json` repeat
 * the collection and field names; change them together.
 *
 * Field names are camelCase, as everywhere outside the entity envelope.
 */

export const AI_JOBS_COLLECTION = "ai_jobs";
export const AI_USAGE_COLLECTION = "ai_usage";

/**
 * Subcollection ids. A TTL policy and a composite index apply to every collection with the same id
 * in the database, so `job` and `input` must not be reused elsewhere: firestore.indexes.json names
 * them as collection groups. `thing` carries neither, and matches
 * `thing_shares/{hostUid}/thing/{thingId}`.
 */
export const AI_JOB_SUBCOLLECTION = "job";
/** Under a job. Its one document is AI_JOB_INPUT_DOC. */
export const AI_JOB_INPUT_SUBCOLLECTION = "input";
export const AI_JOB_INPUT_DOC = "request";
export const AI_USAGE_THING_SUBCOLLECTION = "thing";
export const AI_SPEND_COLLECTION = "ai_spend";
export const AI_COST_LOG_COLLECTION = "ai_cost_log";
export const AI_CACHE_COLLECTION = "ai_cache";
export const AI_CONFIG_COLLECTION = "ai_config";
export const AI_CONFIG_GLOBAL_DOC = "global";

/** How long a finished job stays readable before TTL removes it (PRD R19). */
export const AI_JOB_TTL_MS = 24 * 60 * 60 * 1000;

/**
 * A backstop only: the worker deletes a job's input when it finishes (design §5.8). Firestore never
 * deletes a subcollection with its parent, by TTL or otherwise, so without this a job whose worker
 * never ran would leave the Thing's logs behind it.
 */
export const AI_JOB_INPUT_TTL_MS = AI_JOB_TTL_MS;

/**
 * Cost records are kept at least 13 months (13 × 31 days), so a month can be compared with the same
 * month a year before (§5.6).
 */
export const AI_COST_LOG_TTL_MS = 13 * 31 * 24 * 60 * 60 * 1000;

/**
 * Jobs live under the user who started them, so the rules authorize from the path alone and a
 * client lists its own jobs without a filter it could leave off.
 */
export function aiJobsCollectionPath(callerUid: string): string {
  return `${AI_JOBS_COLLECTION}/${callerUid}/${AI_JOB_SUBCOLLECTION}`;
}

export function aiJobDocPath(callerUid: string, jobId: string): string {
  return `${aiJobsCollectionPath(callerUid)}/${jobId}`;
}

/**
 * The job's request, under the job but in a document of its own: the rules keep it from the caller
 * who can read the job, and the job's listener never downloads it.
 */
export function aiJobInputDocPath(callerUid: string, jobId: string): string {
  return `${aiJobDocPath(callerUid, jobId)}/${AI_JOB_INPUT_SUBCOLLECTION}/${AI_JOB_INPUT_DOC}`;
}

/** One usage document per Thing, under its tree: a Thing id is unique only within one. */
export function aiUsageDocPath(hostUid: string, thingId: string): string {
  return `${AI_USAGE_COLLECTION}/${hostUid}/${AI_USAGE_THING_SUBCOLLECTION}/${thingId}`;
}

/** `yyyymm` in UTC, so every function instance agrees on which month a call belongs to. */
export function aiSpendMonthKey(at: Date): string {
  return `${at.getUTCFullYear()}${String(at.getUTCMonth() + 1).padStart(2, "0")}`;
}

export function aiSpendDocPath(at: Date): string {
  return `${AI_SPEND_COLLECTION}/${aiSpendMonthKey(at)}`;
}

/** A cache key from tasks/cache.ts holds `:`, which a document id may contain. */
export function aiCacheDocPath(key: string): string {
  return `${AI_CACHE_COLLECTION}/${key}`;
}

export const AI_CONFIG_DOC_PATH = `${AI_CONFIG_COLLECTION}/${AI_CONFIG_GLOBAL_DOC}`;

/**
 * `ai_jobs/{callerUid}/job/{jobId}`. Written by functions; read by the caller alone. The composite
 * index on (thingId, kind, createdAt desc) serves "latest job for this Thing". The caller is the
 * path, not a field: nothing in the document can disagree with it.
 *
 * `kind` and `status` hold the proto enums' numbers, which the client reads with Wire's
 * `fromValue`; a number it does not know reads as null rather than as a wrong state.
 */
export type AiJobDoc = {
  kind: AiJobKind;
  /** The Thing's tree. Routing for the worker; the caller is checked against it at start. */
  hostUid: string;
  thingId: string;
  status: AiJobStatus;
  /** The R19 progress text's key, null until the worker reports one. */
  stage: PipelineStage | null;
  /** The stage's argument, such as the document being read. */
  stageArg: string | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
  /** TTL field. `createdAt` + AI_JOB_TTL_MS. */
  expiresAt: Timestamp;
  /** Base64 of the kind's result proto (`SuggestTasksResult`), set on SUCCEEDED and EMPTY. */
  result: string | null;
  error: AiJobErrorDoc | null;
};

/** The `AiJobError` proto as fields, so the client reads it without decoding anything. */
export type AiJobErrorDoc = {
  code: AiErrorCode;
  /** Which string the client shows, when one code has more than one. Never user text. */
  detailKey: string;
};

/**
 * `ai_jobs/{callerUid}/job/{jobId}/input/request`. Functions only; deleted by the worker when it
 * finishes, and with the job when it closes.
 */
export type AiJobInputDoc = {
  kind: AiJobKind;
  /** Base64 of the kind's request proto (`SuggestTasksRequest`), at most 512 KiB decoded. */
  request: string;
  createdAt: Timestamp;
  /** TTL field, a backstop for a job the worker never finished. */
  expiresAt: Timestamp;
};

/** `ai_usage/{hostUid}/thing/{thingId}`. Functions only. */
export type AiUsageDoc = {
  /** The daily limit runs 24 h from here (PRD R49). Set only on SUCCEEDED. */
  lastSuccessAt: Timestamp | null;
  /**
   * The run in flight, which makes `startAiJob` join it or refuse (R19a). It may be another share
   * member's, so it names the caller as well as the job: that is where the job lives.
   */
  inFlightJob: AiJobRef | null;
};

export type AiJobRef = { callerUid: string; jobId: string };

/** The tier whose spend a call counts against: the Thing owner's, never the caller's. */
export type AiOwnerTier = "free" | "pro";

/** `ai_spend/{yyyymm}`. Functions only. Micro-dollars, incremented with each cost record. */
export type AiSpendDoc = {
  freeMicros: number;
  proMicros: number;
  updatedAt: Timestamp;
};

/**
 * `ai_cost_log/{autoId}`, one per provider, OCR or cache-hit call (§5.6). Functions only. Holds no
 * prompt, document or Thing text, and no uid: `jobId` reaches the job while it lives, and the
 * cost log outlives it on purpose.
 */
export type AiCostLogDoc = {
  kind: AiJobKind;
  jobId: string;
  stage: PipelineCallRecord["stage"];
  provider: string;
  tier: PipelineCallRecord["tier"];
  ownerTier: AiOwnerTier;
  inputTokens: number;
  outputTokens: number;
  pages: number;
  costMicros: number;
  latencyMs: number;
  attempts: number;
  cacheHit: boolean;
  createdAt: Timestamp;
  /** TTL field. `createdAt` + AI_COST_LOG_TTL_MS. */
  expiresAt: Timestamp;
};

/**
 * `ai_cache/{key}`. Functions only. Derived schedule items for a document or a Thing identity,
 * never document bytes, page text or anything from one Thing (§6.5). The key carries the
 * generation version, so a version bump strands old entries rather than serving them.
 */
export type AiCacheDoc = {
  /** A DocumentCacheEntry or IdentityCacheEntry from tasks/cache.ts, as JSON. */
  value: string;
  createdAt: Timestamp;
};

/** `ai_config/global`, written by the team with `npm run ai-config`. Functions read it. */
export type AiConfig = {
  /** The kill switch (PRD R49). Off, no run starts and no uncached call is made. */
  enabled: boolean;
  /** Provider ids from providers/registry.ts. Switching provider is a write here (§5.4). */
  fastProvider: string;
  strongProvider: string;
  /** Monthly ceilings in micro-dollars: per owner tier, and for the whole project. */
  monthlyCeilingMicros: { free: number; pro: number; total: number };
  /** Documents per run (design §5.5). */
  maxDocumentsPerRun: number;
};

/**
 * What the seed script writes. Disabled until phase C ships: the switch is turned on by hand.
 *
 * The ceilings are placeholders until the PRD's open question on limit values is settled. A
 * document run averaged $0.23 in the bake-off (design §12.5).
 */
export const DEFAULT_AI_CONFIG: AiConfig = {
  enabled: false,
  fastProvider: "gemini-3.8-flash",
  strongProvider: "gemini-3.8-flash",
  monthlyCeilingMicros: { free: 50_000_000, pro: 150_000_000, total: 200_000_000 },
  maxDocumentsPerRun: 5,
};

/**
 * The config from `ai_config/global`'s data, failing closed: a missing document, or one whose
 * shape is wrong, reads as disabled rather than as unlimited.
 */
export function parseAiConfig(data: unknown): AiConfig {
  const disabled: AiConfig = { ...DEFAULT_AI_CONFIG, enabled: false };
  if (data == null || typeof data !== "object") return disabled;
  const d = data as Record<string, unknown>;
  const ceilings = d.monthlyCeilingMicros as Record<string, unknown> | undefined;
  const valid =
    typeof d.enabled === "boolean" &&
    isNonEmptyString(d.fastProvider) &&
    isNonEmptyString(d.strongProvider) &&
    ceilings != null &&
    typeof ceilings === "object" &&
    isNonNegative(ceilings.free) &&
    isNonNegative(ceilings.pro) &&
    isNonNegative(ceilings.total) &&
    isNonNegative(d.maxDocumentsPerRun);
  if (!valid) return disabled;
  return {
    enabled: d.enabled as boolean,
    fastProvider: d.fastProvider as string,
    strongProvider: d.strongProvider as string,
    monthlyCeilingMicros: {
      free: ceilings.free as number,
      pro: ceilings.pro as number,
      total: ceilings.total as number,
    },
    maxDocumentsPerRun: d.maxDocumentsPerRun as number,
  };
}

function isNonEmptyString(v: unknown): v is string {
  return typeof v === "string" && v.length > 0;
}

function isNonNegative(v: unknown): v is number {
  return typeof v === "number" && Number.isFinite(v) && v >= 0;
}
