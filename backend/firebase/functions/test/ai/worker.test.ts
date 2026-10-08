import { randomUUID } from "node:crypto";

import { beforeEach, describe, expect, it } from "vitest";

import {
  AI_CONFIG_DOC_PATH,
  AI_COST_LOG_COLLECTION,
  DEFAULT_AI_CONFIG,
  aiJobDocPath,
  aiJobInputDocPath,
  aiSpendDocPath,
  aiUsageDocPath,
  type AiJobRef,
} from "../../src/ai/collections.js";
import { AiError } from "../../src/ai/errors.js";
import { FirestorePipelineCache } from "../../src/ai/firestoreCache.js";
import { handleStartAiJob } from "../../src/ai/jobs.js";
import type { PipelineCallRecord } from "../../src/ai/tasks/pipeline.js";
import { handleAiJob, type AiPipeline, type AiWorkerDeps } from "../../src/ai/worker.js";
import { AiJobKind, AiJobStatus } from "../../src/generated/proto/rpc/ai_job/ai_job.js";
import { SuggestTasksRequest } from "../../src/generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { echoPipeline } from "../../src/ai/echoPipeline.js";
import { thingShareDocPath } from "../../src/sharing/sharingModels.js";
import { adminDb, req } from "../helpers.js";

const NOW = new Date("2026-10-15T12:00:00Z");
const TASKS = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS;
const ECHO = AiJobKind.AI_JOB_KIND_ECHO;

type Ids = { host: string; member: string; thing: string };

const ids = (): Ids => ({ host: `h-${randomUUID()}`, member: `m-${randomUUID()}`, thing: `t-${randomUUID()}` });

async function seedSharedThing({ host, member, thing }: Ids): Promise<void> {
  await adminDb.doc(`users/${host}/thing/${thing}`).set({ deleted: false, payload: "" });
  await adminDb.doc(thingShareDocPath(host, thing)).set({ memberRoles: { [host]: "owner", [member]: "technician" } });
}

function encoded({ host, thing }: Ids): string {
  const request = SuggestTasksRequest.fromPartial({
    thingId: { value: thing },
    hostUid: { value: host },
    entryPoint: "overview",
  });
  return Buffer.from(SuggestTasksRequest.encode(request).finish()).toString("base64");
}

function deps(pipeline: AiPipeline | undefined): AiWorkerDeps {
  return { now: () => NOW, pipelineFor: () => pipeline, cache: new FirestorePipelineCache(() => NOW) };
}

/** Starts a job without running it, so a test can change the world in between. */
async function queued(uid: string, t: Ids, kind = TASKS): Promise<AiJobRef> {
  let ref: AiJobRef | undefined;
  await handleStartAiJob(req(uid, { kind, request: encoded(t) }), async (r) => void (ref = r), NOW);
  return ref!;
}

const job = async (ref: AiJobRef) => (await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).get()).data();
const usage = async (t: Ids) => (await adminDb.doc(aiUsageDocPath(t.host, t.thing)).get()).data();
const inputExists = async (ref: AiJobRef) =>
  (await adminDb.doc(aiJobInputDocPath(ref.callerUid, ref.jobId)).get()).exists;

function call(costMicros: number, stage: PipelineCallRecord["stage"] = "recall"): PipelineCallRecord {
  return {
    stage,
    provider: "gemini-3.8-flash",
    tier: "fast",
    usage: { inputTokens: 1_000, outputTokens: 200, costMicros },
    latencyMs: 1_500,
    attempts: 1,
    cacheHit: false,
    pages: 0,
  };
}

beforeEach(async () => {
  await adminDb.doc(AI_CONFIG_DOC_PATH).set({ ...DEFAULT_AI_CONFIG, enabled: true });
  await adminDb.doc(aiSpendDocPath(NOW)).delete();
});

describe("runAiJob", () => {
  it("round-trips an echo job from start to result, without using up the day", async () => {
    const t = ids();
    await seedSharedThing(t);
    const runNow = async (ref: AiJobRef) => handleAiJob(ref, deps(echoPipeline));

    const { jobId } = await handleStartAiJob(req(t.member, { kind: ECHO, request: encoded(t) }), runNow, NOW);

    const ref = { callerUid: t.member, jobId };
    expect(await job(ref)).toMatchObject({
      kind: ECHO,
      status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED,
      result: encoded(t),
      error: null,
      stage: null,
    });
    expect(await usage(t)).toEqual({ lastSuccessAt: null, inFlightJob: null });
    expect(await inputExists(ref)).toBe(false);
  });

  it("writes stages as it runs, logs every call and charges the owner's tier", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.member, t);
    const seen: unknown[] = [];
    const pipeline: AiPipeline = {
      async run(_request, context) {
        expect(context.ownerTier).toBe("free");
        expect((await job(ref))?.status).toBe(AiJobStatus.AI_JOB_STATUS_RUNNING);
        context.reportStage("reading_document", "Rotax MM.pdf");
        context.recordCall(call(1_200, "extract"));
        context.recordCall(call(300));
        context.recordCall({ ...call(0), cacheHit: true });
        await new Promise((resolve) => setTimeout(resolve, 50));
        seen.push(await job(ref));
        return { status: "succeeded", result: new Uint8Array([1, 2, 3]) };
      },
    };

    await handleAiJob(ref, deps(pipeline));

    expect(seen[0]).toMatchObject({ stage: "reading_document", stageArg: "Rotax MM.pdf" });
    expect(await job(ref)).toMatchObject({ status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED, result: "AQID", stage: null });
    const costs = await adminDb.collection(AI_COST_LOG_COLLECTION).where("jobId", "==", ref.jobId).get();
    expect(costs.docs.map((d) => d.get("costMicros")).sort((a, b) => a - b)).toEqual([0, 300, 1_200]);
    expect(costs.docs[0].data()).toMatchObject({ kind: TASKS, ownerTier: "free", provider: "gemini-3.8-flash" });
    expect((await adminDb.doc(aiSpendDocPath(NOW)).get()).data()).toMatchObject({ freeMicros: 1_500 });
    expect((await usage(t))?.lastSuccessAt?.toMillis()).toBe(NOW.getTime());
    expect((await usage(t))?.inFlightJob).toBeNull();
  });

  it("finishes EMPTY without using up the day", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    await handleAiJob(ref, deps({ run: async () => ({ status: "empty", result: new Uint8Array() }) }));
    expect((await job(ref))?.status).toBe(AiJobStatus.AI_JOB_STATUS_EMPTY);
    expect(await usage(t)).toEqual({ lastSuccessAt: null, inFlightJob: null });
  });

  it.each([
    ["an AiError", new AiError("no_schedule_found", "nothing"), "no_schedule_found"],
    ["any other throw", new Error("boom"), "provider_error"],
  ])("fails with the code of %s and frees the Thing", async (_name, thrown, code) => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    await handleAiJob(ref, deps({ run: async () => Promise.reject(thrown) }));
    expect(await job(ref)).toMatchObject({ status: AiJobStatus.AI_JOB_STATUS_FAILED, error: { code, detailKey: "" }, result: null });
    expect(await usage(t)).toEqual({ lastSuccessAt: null, inFlightJob: null });
    expect(await inputExists(ref)).toBe(false);
  });

  it("re-checks access, so a member removed after start never reaches the model", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.member, t);
    await adminDb.doc(thingShareDocPath(t.host, t.thing)).set({ memberRoles: { [t.host]: "owner" } });
    let ran = false;

    await handleAiJob(ref, deps({ run: async () => ((ran = true), { status: "succeeded", result: new Uint8Array() }) }));

    expect(ran).toBe(false);
    expect((await job(ref))?.error).toEqual({ code: "not_member", detailKey: "" });
  });

  it("stops when the kill switch is turned off after start", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    await adminDb.doc(AI_CONFIG_DOC_PATH).update({ enabled: false });
    await handleAiJob(ref, deps(echoPipeline));
    expect((await job(ref))?.error).toEqual({ code: "disabled", detailKey: "" });
  });

  it("fails a kind with no pipeline registered", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    await handleAiJob(ref, deps(undefined));
    expect((await job(ref))?.error).toEqual({ code: "provider_error", detailKey: "" });
  });

  it("runs a job once, however often it is delivered", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    let runs = 0;
    const counting: AiPipeline = { run: async () => (runs++, { status: "succeeded", result: new Uint8Array() }) };
    await handleAiJob(ref, deps(counting));
    await handleAiJob(ref, deps(counting));
    expect(runs).toBe(1);
  });

  it("does nothing for a job closed before it ran, but deletes its input", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).delete();
    let ran = false;
    await handleAiJob(ref, deps({ run: async () => ((ran = true), { status: "succeeded", result: new Uint8Array() }) }));
    expect(ran).toBe(false);
    expect(await inputExists(ref)).toBe(false);
  });

  it("frees the Thing when its job is closed mid-run", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    const closing: AiPipeline = {
      async run(_request, context) {
        await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).delete();
        context.reportStage("tailoring");
        return { status: "succeeded", result: new Uint8Array([9]) };
      },
    };
    await handleAiJob(ref, deps(closing));
    expect(await job(ref)).toBeUndefined();
    expect((await usage(t))?.inFlightJob).toBeNull();
    expect(await inputExists(ref)).toBe(false);
  });

  it("stops a run closed mid-run at its next check, without a push or using up the day", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    let spentAfterClose = false;
    let finished = false;
    const closing: AiPipeline = {
      async run(_request, context) {
        await context.throwIfClosed();
        await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).delete();
        await context.throwIfClosed();
        spentAfterClose = true;
        return { status: "succeeded", result: new Uint8Array([9]) };
      },
      onFinished: async () => void (finished = true),
    };
    await handleAiJob(ref, deps(closing));
    expect(spentAfterClose).toBe(false);
    expect(finished).toBe(false);
    expect(await job(ref)).toBeUndefined();
    expect((await usage(t))?.inFlightJob).toBeNull();
    expect((await usage(t))?.lastSuccessAt).toBeNull();
    expect(await inputExists(ref)).toBe(false);
  });

  it("neither pushes nor uses up the day for a run closed just as it succeeded", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    let finished = false;
    const closing: AiPipeline = {
      async run() {
        await adminDb.doc(aiJobDocPath(ref.callerUid, ref.jobId)).delete();
        return { status: "succeeded", result: new Uint8Array([9]) };
      },
      onFinished: async () => void (finished = true),
    };
    await handleAiJob(ref, deps(closing));
    expect(finished).toBe(false);
    expect((await usage(t))?.lastSuccessAt).toBeNull();
    expect((await usage(t))?.inFlightJob).toBeNull();
  });

  it("calls onFinished for every outcome, after the outcome is written", async () => {
    const t = ids();
    await seedSharedThing(t);
    const ref = await queued(t.host, t);
    const finishes: unknown[] = [];
    await handleAiJob(
      ref,
      deps({
        run: async () => Promise.reject(new AiError("invalid_output", "bad")),
        onFinished: async (finish) => void finishes.push({ ...finish, written: (await job(ref))?.status }),
      }),
    );
    expect(finishes).toEqual([
      expect.objectContaining({
        ref,
        status: AiJobStatus.AI_JOB_STATUS_FAILED,
        error: { code: "invalid_output", detailKey: "" },
        written: AiJobStatus.AI_JOB_STATUS_FAILED,
      }),
    ]);
  });
});

describe("FirestorePipelineCache", () => {
  it("round-trips a value under its path key, and misses an absent one", async () => {
    const cache = new FirestorePipelineCache(() => NOW);
    const key = `doc/tasks-test/${randomUUID()}`;
    expect(await cache.get(key)).toBeUndefined();
    await cache.set(key, { items: [{ title: "Oil change", interval: [50, "hours"] }], note: null });
    expect(await cache.get(key)).toEqual({ items: [{ title: "Oil change", interval: [50, "hours"] }], note: null });
    expect((await adminDb.doc(`ai_cache/${key}`).get()).get("createdAt").toMillis()).toBe(NOW.getTime());
  });
});
