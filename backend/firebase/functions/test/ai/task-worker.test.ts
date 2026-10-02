import { randomUUID } from "node:crypto";

import { beforeEach, describe, expect, it } from "vitest";

import {
  AI_CONFIG_DOC_PATH,
  AI_COST_LOG_COLLECTION,
  DEFAULT_AI_CONFIG,
  aiJobDocPath,
  aiUsageDocPath,
  type AiJobRef,
} from "../../src/ai/collections.js";
import { FirestorePipelineCache } from "../../src/ai/firestoreCache.js";
import { handleStartAiJob } from "../../src/ai/jobs.js";
import type { AiGenerateRequest, AiProvider } from "../../src/ai/providers/types.js";
import { RECALL_SCHEMA, TAILOR_SCHEMA } from "../../src/ai/tasks/schemas.js";
import type { RecallOutput, TailorOutput } from "../../src/ai/tasks/stageTypes.js";
import { createTaskSuggestionPipeline } from "../../src/ai/tasks/taskSuggestionPipeline.js";
import { GENERATION_VERSION } from "../../src/ai/tasks/version.js";
import { handleAiJob, type AiWorkerDeps } from "../../src/ai/worker.js";
import { AiJobKind, AiJobStatus } from "../../src/generated/proto/rpc/ai_job/ai_job.js";
import {
  SuggestTasksRequest,
  SuggestTasksResult,
} from "../../src/generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { thingShareDocPath } from "../../src/sharing/sharingModels.js";
import { TaskSourceKind } from "../../src/generated/proto/task/task_origin.js";
import { adminDb, req } from "../helpers.js";

// T14: a task-suggestion job without documents, from startAiJob through the worker and the real
// pipeline to the result the app reads, on scripted providers.

const NOW = new Date("2026-10-15T12:00:00Z");
const KIND = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS;

const RECALLED: RecallOutput = {
  identityConfidence: "high",
  items: [
    {
      title: "Spark plug replacement",
      description: "",
      checklist: [],
      intervals: [{ value: 200, unit: "hours" }],
      isOneTime: false,
      componentHint: "engine",
      sourceKind: "common_practice",
      publication: null,
    },
  ],
};

const TAILORED: TailorOutput = {
  suggestions: [
    {
      candidateIds: ["r0"],
      title: "Replace spark plugs",
      rationale: "Rotax recommends it.",
      description: "",
      componentSlotKey: "engine",
      componentHint: null,
      rules: [
        { kind: "meter", every: null, unit: null, meterKey: "engine_hours", interval: 200, months: null, dayOfMonth: null, description: null },
      ],
      isOneTime: false,
      firstDue: [],
      lastDoneLogId: null,
      matchesExistingTaskId: null,
      intervalDifferenceNote: null,
      mergesStaticIndex: null,
      confidence: "high",
    },
  ],
  documents: [],
};

/** Answers recall and tailor by schema, records which model it was asked as, and what it cost. */
function scriptedProviders() {
  const asked: Array<{ id: string; stage: "recall" | "tailor" }> = [];
  const providerFor = (id: string): AiProvider => ({
    id,
    async generate(request: AiGenerateRequest) {
      const stage = request.schema === RECALL_SCHEMA ? "recall" : request.schema === TAILOR_SCHEMA ? "tailor" : null;
      if (stage == null) throw new Error("unexpected stage");
      asked.push({ id, stage });
      return { json: stage === "recall" ? RECALLED : TAILORED, usage: { inputTokens: 1_000, outputTokens: 100, costMicros: 700 } };
    },
  });
  return { asked, providerFor };
}

type Ids = { host: string; thing: string };

async function seedThing(): Promise<Ids> {
  const ids = { host: `h-${randomUUID()}`, thing: `t-${randomUUID()}` };
  await adminDb.doc(`users/${ids.host}/thing/${ids.thing}`).set({ deleted: false, payload: "" });
  await adminDb.doc(thingShareDocPath(ids.host, ids.thing)).set({ memberRoles: { [ids.host]: "owner" } });
  return ids;
}

/** A no-document request for a single-engine airplane, as the app's builder will send it. */
function requestFor({ host, thing }: Ids, make = "Sling"): string {
  const request = SuggestTasksRequest.fromPartial({
    thingId: { value: thing },
    hostUid: { value: host },
    entryPoint: "overview",
    context: {
      templateId: { value: "airplane" },
      templateVersion: 13,
      specs: [{ key: "make", label: "Make", value: make }],
      components: [{ slotKey: "engine", make: "Rotax", model: "915 iS", spec: [] }],
      meters: [{ key: "engine_hours", unitLabel: "hrs", componentSlotKey: "engine", current: 410, hasCurrent: true }],
      lexiconTaskNoun: "inspection",
    },
  });
  return Buffer.from(SuggestTasksRequest.encode(request).finish()).toString("base64");
}

async function runJob(ids: Ids, providerFor: (id: string) => AiProvider, make?: string): Promise<AiJobRef> {
  const deps: AiWorkerDeps = {
    now: () => NOW,
    pipelineFor: () => createTaskSuggestionPipeline(providerFor),
    cache: new FirestorePipelineCache(() => NOW),
  };
  const { jobId } = await handleStartAiJob(
    req(ids.host, { kind: KIND, request: requestFor(ids, make) }),
    async (ref) => handleAiJob(ref, deps),
    NOW,
  );
  return { callerUid: ids.host, jobId };
}

beforeEach(async () => {
  await adminDb.doc(AI_CONFIG_DOC_PATH).set({ ...DEFAULT_AI_CONFIG, enabled: true });
});

describe("the task-suggestion pipeline in the worker", () => {
  it("runs a no-document job to a result the app can decode", async () => {
    const ids = await seedThing();
    const { asked, providerFor } = scriptedProviders();

    const ref = await runJob(ids, providerFor, `Sling-${randomUUID()}`);

    const job = (await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).get()).data();
    expect(job).toMatchObject({ kind: KIND, status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED, error: null, stage: null });
    const result = SuggestTasksResult.decode(Buffer.from(job!.result, "base64"));
    expect(result.generationVersion).toBe(GENERATION_VERSION);
    expect(result.suggestions).toEqual([
      expect.objectContaining({
        suggestionId: { value: "s1" },
        title: "Replace spark plugs",
        componentSlotKey: "engine",
        rules: [{ meterRule: { meterKey: "engine_hours", interval: 200 } }],
        sourceKind: TaskSourceKind.TASK_SOURCE_KIND_COMMON_PRACTICE,
      }),
    ]);

    // Both tiers on the configured model: recall on fast, tailor on strong.
    expect(asked).toEqual([
      { id: DEFAULT_AI_CONFIG.fastProvider, stage: "recall" },
      { id: DEFAULT_AI_CONFIG.strongProvider, stage: "tailor" },
    ]);
    const costs = await adminDb.collection(AI_COST_LOG_COLLECTION).where("jobId", "==", ref.jobId).get();
    expect(costs.docs.map((d) => d.get("stage")).sort()).toEqual(["recall", "tailor"]);
    // A successful run uses up the Thing's day (R49).
    expect((await adminDb.doc(aiUsageDocPath(ids.host, ids.thing)).get()).get("lastSuccessAt")?.toMillis()).toBe(
      NOW.getTime(),
    );
  });

  it("serves the second identical Thing's recall from the cache", async () => {
    const make = `Sling-${randomUUID()}`;
    const first = scriptedProviders();
    await runJob(await seedThing(), first.providerFor, make);

    const second = scriptedProviders();
    const ref = await runJob(await seedThing(), second.providerFor, make);

    expect(second.asked.map((a) => a.stage)).toEqual(["tailor"]);
    const costs = await adminDb.collection(AI_COST_LOG_COLLECTION).where("jobId", "==", ref.jobId).get();
    expect(costs.docs.find((d) => d.get("stage") === "recall")?.get("cacheHit")).toBe(true);
  });

  it("fails a job whose provider fails, without using up the day", async () => {
    const ids = await seedThing();
    const failing = (id: string): AiProvider => ({
      id,
      async generate() {
        throw new Error("Vertex is down");
      },
    });

    const ref = await runJob(ids, failing, `Sling-${randomUUID()}`);

    const job = (await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).get()).data();
    expect(job).toMatchObject({ status: AiJobStatus.AI_JOB_STATUS_FAILED, error: { code: "provider_error" } });
    expect((await adminDb.doc(aiUsageDocPath(ids.host, ids.thing)).get()).get("lastSuccessAt")).toBeNull();
  });
});
