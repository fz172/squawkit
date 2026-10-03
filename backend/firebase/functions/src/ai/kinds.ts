import { AiJobKind } from "../generated/proto/rpc/ai_job/ai_job.js";
import { SuggestTasksRequest, SuggestTasksResult } from "../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { curatedResult } from "./tasks/curatedResult.js";
import { requestFromProto, resultToProto } from "./tasks/wire.js";

/**
 * What the callables and the worker need to know about each kind of AI job before any pipeline
 * runs: which Thing a request is about, and whether a success uses up the day. The pipelines
 * themselves are registered with the worker (worker.ts); this stays light so the callables do not
 * load them.
 */

export type AiJobTarget = {
  hostUid: string;
  thingId: string;
  documentCount: number;
};

export type AiJobKindSpec = {
  kind: AiJobKind;
  /** The Thing a request names. Throws if the bytes do not decode. */
  decodeTarget(request: Uint8Array): AiJobTarget;
  /** Whether a SUCCEEDED run sets `lastSuccessAt` (PRD R49). */
  countsTowardDailyLimit: boolean;
  /**
   * Whether this deploy runs the kind with documents. False for task suggestions until T21 wires
   * stages 1–2: start refuses a request with documents, and eligibility offers none.
   */
  acceptsDocuments: boolean;
  /**
   * The result a job carries from the moment it is written, before any worker runs: the task
   * kind's curated suggestions (design §6.8), so the app shows them within a second. Null when
   * the kind has none, or the Thing's template has no curated list.
   */
  initialResult(request: Uint8Array): Uint8Array | null;
};

function suggestTasksTarget(request: Uint8Array): AiJobTarget {
  const decoded = SuggestTasksRequest.decode(request);
  return {
    hostUid: decoded.hostUid?.value ?? "",
    thingId: decoded.thingId?.value ?? "",
    documentCount: decoded.documents.length,
  };
}

function curatedTaskResult(request: Uint8Array): Uint8Array | null {
  const result = curatedResult(requestFromProto(SuggestTasksRequest.decode(request)).context);
  if (result.suggestions.length === 0) return null;
  return SuggestTasksResult.encode(resultToProto(result)).finish();
}

const SPECS: AiJobKindSpec[] = [
  {
    kind: AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
    decodeTarget: suggestTasksTarget,
    countsTowardDailyLimit: true,
    acceptsDocuments: false,
    initialResult: curatedTaskResult,
  },
  // The developer round trip takes a task request too, so a client tests the real encoding.
  {
    kind: AiJobKind.AI_JOB_KIND_ECHO,
    decodeTarget: suggestTasksTarget,
    countsTowardDailyLimit: false,
    acceptsDocuments: true,
    initialResult: () => null,
  },
];

/** The spec for a kind a client sent, or null for a kind this deploy does not know. */
export function aiJobKindSpec(kind: unknown): AiJobKindSpec | null {
  return SPECS.find((s) => s.kind === kind) ?? null;
}
