import { AiJobKind } from "../generated/proto/rpc/ai_job/ai_job.js";
import { SuggestTasksRequest } from "../generated/proto/rpc/suggest_tasks/suggest_tasks.js";

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
};

function suggestTasksTarget(request: Uint8Array): AiJobTarget {
  const decoded = SuggestTasksRequest.decode(request);
  return {
    hostUid: decoded.hostUid?.value ?? "",
    thingId: decoded.thingId?.value ?? "",
    documentCount: decoded.documents.length,
  };
}

const SPECS: AiJobKindSpec[] = [
  {
    kind: AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS,
    decodeTarget: suggestTasksTarget,
    countsTowardDailyLimit: true,
    acceptsDocuments: false,
  },
  // The developer round trip takes a task request too, so a client tests the real encoding.
  {
    kind: AiJobKind.AI_JOB_KIND_ECHO,
    decodeTarget: suggestTasksTarget,
    countsTowardDailyLimit: false,
    acceptsDocuments: true,
  },
];

/** The spec for a kind a client sent, or null for a kind this deploy does not know. */
export function aiJobKindSpec(kind: unknown): AiJobKindSpec | null {
  return SPECS.find((s) => s.kind === kind) ?? null;
}
