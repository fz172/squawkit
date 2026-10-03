import { createHash, randomUUID } from "node:crypto";

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
import { EXTRACT_SCHEMA, RECALL_SCHEMA, TAILOR_SCHEMA } from "../../src/ai/tasks/schemas.js";
import type { ExtractOutput, RecallOutput, TailorOutput } from "../../src/ai/tasks/stageTypes.js";
import { createTaskSuggestionPipeline } from "../../src/ai/tasks/taskSuggestionPipeline.js";
import { GENERATION_VERSION } from "../../src/ai/tasks/version.js";
import { handleAiJob, type AiJobFinish, type AiWorkerDeps } from "../../src/ai/worker.js";
import { AiJobKind, AiJobStatus } from "../../src/generated/proto/rpc/ai_job/ai_job.js";
import {
  SuggestTasksRequest,
  SuggestTasksResult,
} from "../../src/generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { thingShareDocPath } from "../../src/sharing/sharingModels.js";
import { TaskOriginKind, TaskSourceKind } from "../../src/generated/proto/task/task_origin.js";
import { curatedListFor } from "../../src/ai/tasks/curated.js";
import { PDFDocument, StandardFonts } from "pdf-lib";
import { adminStorage } from "../../src/config/firebaseAdmin.js";
import { blobObjectPath } from "../../src/storage/blobBroker.js";
import { SUBSCRIPTION_LIFECYCLE, SUBSCRIPTION_STATUS, subscriptionDocPath } from "../../src/subscription/entitlementModel.js";
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
      // The model points at a log; the result must not (owner's decision, 2026-10-02).
      lastDoneLogId: "log-1",
      matchesExistingTaskId: null,
      intervalDifferenceNote: null,
      mergesStaticIndex: null,
      confidence: "high",
    },
  ],
  documents: [],
  notApplicable: [],
};

/** Answers recall and tailor by schema, records which model it was asked as, and what it cost. */
function scriptedProviders(recalled: RecallOutput = RECALLED, tailored: TailorOutput = TAILORED) {
  const asked: Array<{ id: string; stage: "recall" | "tailor" }> = [];
  const providerFor = (id: string): AiProvider => ({
    id,
    async generate(request: AiGenerateRequest) {
      const stage = request.schema === RECALL_SCHEMA ? "recall" : request.schema === TAILOR_SCHEMA ? "tailor" : null;
      if (stage == null) throw new Error("unexpected stage");
      asked.push({ id, stage });
      return { json: stage === "recall" ? recalled : tailored, usage: { inputTokens: 1_000, outputTokens: 100, costMicros: 700 } };
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
      logs: [
        {
          id: { value: "log-1" },
          date: "2026-05-02",
          readings: [{ meterKey: "engine_hours", value: 380 }],
          workDescription: "Replaced spark plugs",
          componentSlotKey: "engine",
        },
      ],
    },
  });
  return Buffer.from(SuggestTasksRequest.encode(request).finish()).toString("base64");
}

async function runJob(
  ids: Ids,
  providerFor: (id: string) => AiProvider,
  make?: string,
  finished: AiJobFinish[] = [],
): Promise<AiJobRef> {
  const deps: AiWorkerDeps = {
    now: () => NOW,
    pipelineFor: () => createTaskSuggestionPipeline(providerFor, async (finish) => void finished.push(finish)),
    cache: new FirestorePipelineCache(() => NOW),
  };
  const { jobId } = await handleStartAiJob(
    req(ids.host, { kind: KIND, request: requestFor(ids, make) }),
    async (ref) => handleAiJob(ref, deps),
    NOW,
  );
  return { callerUid: ids.host, jobId };
}

function titlesOf(result: string): string[] {
  return SuggestTasksResult.decode(Buffer.from(result, "base64")).suggestions.map((s) => s.title);
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
    // The AI's suggestion first, then the curated list it did not cover (design §6.8).
    const curatedTitles = curatedListFor("airplane").map((c) => c.title);
    expect(result.suggestions.map((s) => s.title)).toEqual(["Replace spark plugs", ...curatedTitles]);
    expect(result.suggestions.map((s) => s.originKind)).toEqual([
      TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
      ...curatedTitles.map(() => TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED),
    ]);
    expect(result.suggestions.slice(0, 1)).toEqual([
      expect.objectContaining({
        suggestionId: { value: "s1" },
        title: "Replace spark plugs",
        componentSlotKey: "engine",
        rules: [{ meterRule: { meterKey: "engine_hours", interval: 200 } }],
        sourceKind: TaskSourceKind.TASK_SOURCE_KIND_COMMON_PRACTICE,
        // Logs shaped the run, but no suggestion is tied to one.
        lastDone: undefined,
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
    // The curated suggestions the job was written with stay, for the app to show under the failure.
    expect(titlesOf(job!.result)).toEqual(curatedListFor("airplane").map((c) => c.title));
  });

  it("replaces the curated card an AI suggestion covers, and keeps the rest", async () => {
    const oilIndex = curatedListFor("airplane").findIndex((c) => c.title === "Oil change");
    const covering: TailorOutput = {
      ...TAILORED,
      suggestions: [{ ...TAILORED.suggestions[0], title: "Oil and filter change", mergesStaticIndex: oilIndex }],
    };
    const { providerFor } = scriptedProviders(RECALLED, covering);

    const ref = await runJob(await seedThing(), providerFor, `Sling-${randomUUID()}`);

    const titles = titlesOf((await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).get()).get("result"));
    expect(titles[0]).toBe("Oil and filter change");
    expect(titles).not.toContain("Oil change");
    expect(titles).toHaveLength(curatedListFor("airplane").length);
  });

  it("tells the person who started it how the run ended, once, after the outcome is written (R20)", async () => {
    const ids = await seedThing();
    const finished: AiJobFinish[] = [];

    const ref = await runJob(ids, scriptedProviders().providerFor, `Sling-${randomUUID()}`, finished);

    expect(finished).toHaveLength(1);
    expect(finished[0]).toMatchObject({ ref, status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED });
    expect(finished[0].job.thingId).toBe(ids.thing);
  });

  it("tells them when the run failed too", async () => {
    const finished: AiJobFinish[] = [];
    const failing = (id: string): AiProvider => ({
      id,
      async generate() {
        throw new Error("Vertex is down");
      },
    });

    await runJob(await seedThing(), failing, `Sling-${randomUUID()}`, finished);

    expect(finished.map((f) => f.status)).toEqual([AiJobStatus.AI_JOB_STATUS_FAILED]);
  });

  it("leaves out the curated items the model says do not fit this Thing", async () => {
    // The EV case: an engine oil change makes no sense on a battery-electric car.
    const oilIndex = curatedListFor("airplane").findIndex((c) => c.title === "Oil change");
    const judging: TailorOutput = { ...TAILORED, notApplicable: [{ index: oilIndex, reason: "no engine oil" }] };
    const { providerFor } = scriptedProviders(RECALLED, judging);

    const ref = await runJob(await seedThing(), providerFor, `Sling-${randomUUID()}`);

    const titles = titlesOf((await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).get()).get("result"));
    expect(titles).not.toContain("Oil change");
    expect(titles).toHaveLength(curatedListFor("airplane").length); // the model's one, minus one curated
  });

  it("ends EMPTY with the whole curated list when the model has nothing confident", async () => {
    const { providerFor } = scriptedProviders({ ...RECALLED, identityConfidence: "low" });

    const ref = await runJob(await seedThing(), providerFor, `Sling-${randomUUID()}`);

    const job = (await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).get()).data();
    expect(job?.status).toBe(AiJobStatus.AI_JOB_STATUS_EMPTY);
    expect(titlesOf(job!.result)).toEqual(curatedListFor("airplane").map((c) => c.title));
  });
});

// T21: a run with a document, from the Thing's blob in Storage through the real pipeline.
describe("a task-suggestion run with a document", () => {
  const EXTRACTION: ExtractOutput = {
    document: {
      manufacturer: "Rotax",
      models: ["915 iS"],
      title: "Rotax 915 iS Maintenance Manual",
      revision: "3",
      docType: "maintenance_manual",
      referenceNumber: null,
    },
    items: [
      {
        title: "Replace spark plugs",
        description: "",
        checklist: [],
        intervals: [{ value: 200, unit: "hours" }],
        isOneTime: false,
        pages: [2],
        printedPageRef: "5-12",
        componentHint: "engine",
        type: "routine",
        referenceNumber: null,
        complianceAuthority: null,
      },
    ],
  };
  const FROM_DOCUMENT: TailorOutput = {
    suggestions: [{ ...TAILORED.suggestions[0], candidateIds: ["d0.0"], lastDoneLogId: null }],
    documents: [{ index: 0, matchesThing: true }],
    notApplicable: [],
  };

  /** Answers extract, recall and tailor by schema. */
  function documentProviders(): (id: string) => AiProvider {
    return (id) => ({
      id,
      async generate(request: AiGenerateRequest) {
        const json =
          request.schema === EXTRACT_SCHEMA
            ? EXTRACTION
            : request.schema === RECALL_SCHEMA
              ? RECALLED
              : request.schema === TAILOR_SCHEMA
                ? FROM_DOCUMENT
                : null;
        if (json == null) throw new Error("unexpected stage");
        return { json, usage: { inputTokens: 1_000, outputTokens: 100, costMicros: 700 } };
      },
    });
  }

  async function manual(): Promise<Uint8Array> {
    const pdf = await PDFDocument.create();
    const font = await pdf.embedFont(StandardFonts.Helvetica);
    pdf.addPage().drawText("Rotax 915 iS Maintenance Manual, revision 3", { x: 40, y: 700, font, size: 10 });
    pdf.addPage().drawText("5-12 Scheduled maintenance: replace spark plugs every 200 hours.", { x: 40, y: 700, font, size: 10 });
    return pdf.save();
  }

  it("reads the Thing's document and cites it", async () => {
    const ids = await seedThing();
    await adminDb.doc(subscriptionDocPath(ids.host)).set({
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      willRenew: true,
      currentPeriodEndMillis: NOW.getTime() + 30 * 24 * 60 * 60 * 1000,
    });
    const bytes = await manual();
    const blobId = `b-${randomUUID()}`;
    await adminStorage.bucket().file(blobObjectPath(ids.host, ids.thing, blobId)).save(Buffer.from(bytes));
    const base = SuggestTasksRequest.decode(Buffer.from(requestFor(ids, `Sling-${randomUUID()}`), "base64"));
    const request = SuggestTasksRequest.encode({
      ...base,
      documents: [
        {
          blobId: { value: blobId },
          name: "Rotax MM.pdf",
          mimeType: "application/pdf",
          sha256: createHash("sha256").update(bytes).digest("hex"),
          sizeBytes: bytes.length,
        },
      ],
    }).finish();
    const deps: AiWorkerDeps = {
      now: () => NOW,
      pipelineFor: () => createTaskSuggestionPipeline(documentProviders(), async () => {}),
      cache: new FirestorePipelineCache(() => NOW),
    };

    const { jobId } = await handleStartAiJob(
      req(ids.host, { kind: KIND, request: Buffer.from(request).toString("base64") }),
      async (ref) => handleAiJob(ref, deps),
      NOW,
    );

    const job = (await adminDb.doc(aiJobDocPath(ids.host, jobId)).get()).data();
    expect(job?.status).toBe(AiJobStatus.AI_JOB_STATUS_SUCCEEDED);
    const result = SuggestTasksResult.decode(Buffer.from(job!.result, "base64"));
    expect(result.suggestions[0]).toMatchObject({
      title: "Replace spark plugs",
      sourceKind: TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT,
      sourceDocument: { value: blobId },
      originKind: TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT,
      sourcePages: [2],
    });
    expect(result.documents).toEqual([
      expect.objectContaining({ blobId: { value: blobId }, title: "Rotax 915 iS Maintenance Manual", matchesThing: true }),
    ]);
  });
});
