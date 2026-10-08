import { randomUUID } from "node:crypto";

import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { beforeEach, describe, expect, it } from "vitest";

import {
  AI_CONFIG_DOC_PATH,
  DEFAULT_AI_CONFIG,
  aiJobDocPath,
  aiJobInputDocPath,
  aiUsageDocPath,
  type AiJobRef,
} from "../../src/ai/collections.js";
import {
  AI_JOB_STALE_MS,
  handleCloseAiJob,
  handleGetAiEligibility,
  handleStartAiJob,
} from "../../src/ai/jobs.js";
import { AiJobKind, AiJobStatus } from "../../src/generated/proto/rpc/ai_job/ai_job.js";
import { SuggestTasksRequest, SuggestTasksResult } from "../../src/generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { TaskOriginKind } from "../../src/generated/proto/task/task_origin.js";
import { curatedListFor } from "../../src/ai/tasks/curated.js";
import { thingShareDocPath } from "../../src/sharing/sharingModels.js";
import {
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  subscriptionDocPath,
} from "../../src/subscription/entitlementModel.js";
import { adminDb, req } from "../helpers.js";

const NOW = new Date("2026-10-15T12:00:00Z");
const KIND = AiJobKind.AI_JOB_KIND_TASK_SUGGESTIONS;
const MINUTE = 60 * 1000;

type Ids = { host: string; member: string; thing: string };

function ids(): Ids {
  return { host: `h-${randomUUID()}`, member: `m-${randomUUID()}`, thing: `t-${randomUUID()}` };
}

/** A shared Thing: `host` owns it and `member` is a technician. */
async function seedSharedThing({ host, member, thing }: Ids): Promise<void> {
  await adminDb.doc(`users/${host}/thing/${thing}`).set({ deleted: false, payload: "" });
  await adminDb.doc(thingShareDocPath(host, thing)).set({ memberRoles: { [host]: "owner", [member]: "technician" } });
}

function encoded({ host, thing }: Ids, documents = 0, templateId = "", curatedOnly = false, documentBytes = 1024): string {
  const request = SuggestTasksRequest.fromPartial({
    thingId: { value: thing },
    hostUid: { value: host },
    documents: Array.from({ length: documents }, (_, i) => ({ name: `doc-${i}.pdf`, sizeBytes: documentBytes })),
    context: { templateId: { value: templateId } },
    curatedOnly,
  });
  return Buffer.from(SuggestTasksRequest.encode(request).finish()).toString("base64");
}

async function makePro(uid: string): Promise<void> {
  await adminDb.doc(subscriptionDocPath(uid)).set({
    status: SUBSCRIPTION_STATUS.PRO,
    lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
    willRenew: true,
    currentPeriodEndMillis: NOW.getTime() + 30 * 24 * 60 * MINUTE,
  });
}

function recorder(): { dispatched: AiJobRef[]; dispatch: (ref: AiJobRef) => Promise<void> } {
  const dispatched: AiJobRef[] = [];
  return { dispatched, dispatch: async (ref) => void dispatched.push(ref) };
}

async function errorOf(promise: Promise<unknown>): Promise<HttpsError> {
  const error = await promise.then(
    () => null,
    (e: unknown) => e,
  );
  expect(error).toBeInstanceOf(HttpsError);
  return error as HttpsError;
}

const start = (uid: string, t: Ids, dispatch = recorder().dispatch, now = NOW) =>
  handleStartAiJob(req(uid, { kind: KIND, request: encoded(t) }), dispatch, now);

const startRequest = (uid: string, request: string, dispatch = recorder().dispatch) =>
  handleStartAiJob(req(uid, { kind: KIND, request }), dispatch, NOW);

const eligibility = (uid: string, t: Ids, provider?: string) =>
  handleGetAiEligibility(req(uid, { kind: KIND, thingId: t.thing, hostUid: t.host, withDocuments: false }, provider), NOW);

beforeEach(async () => {
  await adminDb.doc(AI_CONFIG_DOC_PATH).set({ ...DEFAULT_AI_CONFIG, enabled: true });
});

describe("startAiJob", () => {
  it("writes the job, its input and the Thing's in-flight pointer, then dispatches", async () => {
    const t = ids();
    await seedSharedThing(t);
    const { dispatched, dispatch } = recorder();

    const { jobId, joined } = await start(t.host, t, dispatch);

    expect(joined).toBe(false);
    expect(dispatched).toEqual([{ callerUid: t.host, jobId }]);
    const job = (await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).data();
    expect(job).toMatchObject({
      kind: KIND,
      hostUid: t.host,
      thingId: t.thing,
      status: AiJobStatus.AI_JOB_STATUS_QUEUED,
      stage: null,
      result: null,
      error: null,
    });
    expect(job?.expiresAt.toMillis() - NOW.getTime()).toBe(24 * 60 * MINUTE);
    expect((await adminDb.doc(aiJobInputDocPath(t.host, jobId)).get()).get("request")).toBe(encoded(t));
    expect((await adminDb.doc(aiUsageDocPath(t.host, t.thing)).get()).data()).toEqual({
      lastSuccessAt: null,
      inFlightJob: { callerUid: t.host, jobId },
    });
  });

  it("writes the curated suggestions into the new job, before any worker runs (design §6.8)", async () => {
    const t = ids();
    await seedSharedThing(t);

    const { jobId } = await handleStartAiJob(req(t.host, { kind: KIND, request: encoded(t, 0, "airplane") }), recorder().dispatch, NOW);

    const job = (await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).data();
    expect(job?.status).toBe(AiJobStatus.AI_JOB_STATUS_QUEUED);
    const result = SuggestTasksResult.decode(Buffer.from(job?.result, "base64"));
    expect(result.suggestions.map((s) => s.title)).toEqual(curatedListFor("airplane").map((c) => c.title));
    expect(result.suggestions.every((s) => s.originKind === TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED)).toBe(true);
  });

  it("writes no result for a template with no curated list", async () => {
    const t = ids();
    await seedSharedThing(t);

    const { jobId } = await handleStartAiJob(req(t.host, { kind: KIND, request: encoded(t, 0, "custom") }), recorder().dispatch, NOW);

    expect((await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).get("result")).toBeNull();
  });

  it("joins the caller's own run in flight instead of starting a second", async () => {
    const t = ids();
    await seedSharedThing(t);
    const first = await start(t.host, t);
    const { dispatched, dispatch } = recorder();

    expect(await start(t.host, t, dispatch)).toEqual({ jobId: first.jobId, joined: true, ai: null });
    expect(dispatched).toEqual([]);
  });

  it("refuses another member while a run is in flight (R19a)", async () => {
    const t = ids();
    await seedSharedThing(t);
    await start(t.host, t);

    const error = await errorOf(start(t.member, t));
    expect(error.details).toEqual({ code: "run_in_progress", nextAvailableAt: null });
  });

  it("fails a stale run, deletes its input and starts a new one", async () => {
    const t = ids();
    await seedSharedThing(t);
    const old = await start(t.host, t);
    const later = new Date(NOW.getTime() + AI_JOB_STALE_MS + MINUTE);

    const fresh = await start(t.member, t, recorder().dispatch, later);

    expect(fresh.joined).toBe(false);
    expect((await adminDb.doc(aiJobDocPath(t.host, old.jobId)).get()).data()).toMatchObject({
      status: AiJobStatus.AI_JOB_STATUS_FAILED,
      error: { code: "stale", detailKey: "" },
    });
    expect((await adminDb.doc(aiJobInputDocPath(t.host, old.jobId)).get()).exists).toBe(false);
    expect((await adminDb.doc(aiUsageDocPath(t.host, t.thing)).get()).get("inFlightJob")).toEqual({
      callerUid: t.member,
      jobId: fresh.jobId,
    });
  });

  it("starts anew when the pointer names a finished or deleted job", async () => {
    const t = ids();
    await seedSharedThing(t);
    const first = await start(t.host, t);
    await adminDb.doc(aiJobDocPath(t.host, first.jobId)).update({ status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED });
    const second = await start(t.member, t);
    expect(second.joined).toBe(false);

    await adminDb.doc(aiJobDocPath(t.member, second.jobId)).delete();
    expect((await start(t.host, t)).joined).toBe(false);
  });

  it("fails the job and frees the Thing when it cannot be dispatched", async () => {
    const t = ids();
    await seedSharedThing(t);
    const failing = async () => {
      throw new Error("queue unavailable");
    };

    const error = await errorOf(start(t.host, t, failing));

    expect(error).toMatchObject({ code: "unavailable", details: { code: "provider_error", nextAvailableAt: null } });
    const usage = (await adminDb.doc(aiUsageDocPath(t.host, t.thing)).get()).data();
    expect(usage?.inFlightJob).toBeNull();
    const jobs = await adminDb.collection(`ai_jobs/${t.host}/job`).get();
    expect(jobs.docs.map((d) => d.get("status"))).toEqual([AiJobStatus.AI_JOB_STATUS_FAILED]);
    expect((await adminDb.doc(aiJobInputDocPath(t.host, jobs.docs[0].id)).get()).exists).toBe(false);
    expect((await start(t.member, t)).joined).toBe(false);
  });

  it("passes authorization's refusal through", async () => {
    const t = ids();
    await seedSharedThing(t);

    expect((await errorOf(start(randomUUID(), t))).details).toMatchObject({ code: "not_member" });
  });

  it("returns the curated suggestions alone when the day's run is used, saying when AI is back (R9a)", async () => {
    const t = ids();
    await seedSharedThing(t);
    await adminDb.doc(aiUsageDocPath(t.host, t.thing)).set({
      lastSuccessAt: Timestamp.fromMillis(NOW.getTime() - 60 * MINUTE),
      inFlightJob: null,
    });
    const { dispatched, dispatch } = recorder();

    const { jobId, joined } = await startRequest(t.host, encoded(t, 0, "airplane"), dispatch);

    expect(joined).toBe(false);
    expect(dispatched).toEqual([]);
    const job = (await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).data();
    expect(job?.status).toBe(AiJobStatus.AI_JOB_STATUS_SUCCEEDED);
    expect(job?.aiSkipped.code).toBe("daily_limit");
    expect(job?.aiSkipped.nextAvailableAt.toMillis()).toBe(NOW.getTime() + 23 * 60 * MINUTE);
    expect(SuggestTasksResult.decode(Buffer.from(job?.result, "base64")).suggestions).toHaveLength(curatedListFor("airplane").length);
  });

  it("ends a curated-only request at once: no input, no worker, the Thing not held", async () => {
    const t = ids();
    await seedSharedThing(t);
    const { dispatched, dispatch } = recorder();

    const { jobId } = await startRequest(t.host, encoded(t, 0, "airplane", true), dispatch);

    expect(dispatched).toEqual([]);
    const job = (await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).data();
    expect(job).toMatchObject({ status: AiJobStatus.AI_JOB_STATUS_SUCCEEDED, aiSkipped: null, error: null });
    expect((await adminDb.doc(aiJobInputDocPath(t.host, jobId)).get()).exists).toBe(false);
    expect((await adminDb.doc(aiUsageDocPath(t.host, t.thing)).get()).get("inFlightJob") ?? null).toBeNull();
    // Nothing held, so an AI run can start straight after.
    expect((await start(t.host, t)).joined).toBe(false);
  });

  it("answers a curated-only request with whether a model run can start, as eligibility would", async () => {
    const t = ids();
    await seedSharedThing(t);
    await makePro(t.host);

    const started = await startRequest(t.member, encoded(t, 0, "airplane", true));

    expect(started.ai).toEqual({ allowed: true, reason: null, documentsAllowed: true, nextAvailableAt: null });
    expect(started.ai).toEqual(await eligibility(t.member, t));
  });

  it("answers a curated-only request with why the model cannot run, and when it can", async () => {
    const t = ids();
    await seedSharedThing(t);
    await adminDb.doc(aiUsageDocPath(t.host, t.thing)).set({
      lastSuccessAt: Timestamp.fromMillis(NOW.getTime() - 60 * MINUTE),
      inFlightJob: null,
    });

    const { jobId, ai } = await startRequest(t.host, encoded(t, 0, "airplane", true));

    expect(ai).toEqual({
      allowed: false,
      reason: "daily_limit",
      documentsAllowed: false,
      nextAvailableAt: new Date(NOW.getTime() + 23 * 60 * MINUTE).toISOString(),
    });
    // The curated list was asked for, not fallen back to, so the job itself says nothing skipped.
    expect((await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).get("aiSkipped")).toBeNull();
  });

  it("says nothing about eligibility on a model run", async () => {
    const t = ids();
    await seedSharedThing(t);
    expect((await start(t.host, t)).ai).toBeNull();
  });

  it("ends a curated-only request EMPTY for a template with no curated list", async () => {
    const t = ids();
    await seedSharedThing(t);

    const { jobId } = await startRequest(t.host, encoded(t, 0, "custom", true));

    const job = (await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).data();
    expect(job).toMatchObject({ status: AiJobStatus.AI_JOB_STATUS_EMPTY, result: null });
  });

  it("joins the caller's own run in flight on a curated-only request, which carries the same list", async () => {
    const t = ids();
    await seedSharedThing(t);
    const running = await start(t.host, t);

    expect(await startRequest(t.host, encoded(t, 0, "airplane", true))).toMatchObject({ jobId: running.jobId, joined: true });
  });

  it("still refuses a curated-only request from outside the share", async () => {
    const t = ids();
    await seedSharedThing(t);

    expect((await errorOf(startRequest(randomUUID(), encoded(t, 0, "airplane", true)))).details).toMatchObject({ code: "not_member" });
  });

  it("allows the configured number of documents and refuses one more", async () => {
    const allowed = ids();
    const over = ids();
    for (const t of [allowed, over]) {
      await seedSharedThing(t);
      await adminDb.doc(subscriptionDocPath(t.host)).set({
        status: SUBSCRIPTION_STATUS.PRO,
        lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
        willRenew: true,
        currentPeriodEndMillis: NOW.getTime() + 30 * 24 * 60 * MINUTE,
      });
    }
    // The cap is the config's, for any kind that takes documents.
    const withDocs = (t: Ids, n: number) =>
      handleStartAiJob(
        req(t.host, { kind: AiJobKind.AI_JOB_KIND_ECHO, request: encoded(t, n) }),
        recorder().dispatch,
        NOW,
      );

    expect((await withDocs(allowed, DEFAULT_AI_CONFIG.maxDocumentsPerRun)).joined).toBe(false);
    expect((await errorOf(withDocs(over, DEFAULT_AI_CONFIG.maxDocumentsPerRun + 1))).code).toBe("invalid-argument");
  });

  it("takes documents on a Pro owner's task run, and refuses a free owner's (T21)", async () => {
    const pro = ids();
    const free = ids();
    await seedSharedThing(pro);
    await seedSharedThing(free);
    await makePro(pro.host);

    const ok = await handleStartAiJob(req(pro.host, { kind: KIND, request: encoded(pro, 1) }), recorder().dispatch, NOW);
    const refused = handleStartAiJob(req(free.host, { kind: KIND, request: encoded(free, 1) }), recorder().dispatch, NOW);

    expect(ok.joined).toBe(false);
    expect((await errorOf(refused)).details).toMatchObject({ code: "owner_not_pro" });
  });

  it("refuses a document over maxDocumentBytes before any spend", async () => {
    const t = ids();
    await seedSharedThing(t);
    await makePro(t.host);
    const { dispatched, dispatch } = recorder();
    const tooBig = encoded(t, 1, "", false, DEFAULT_AI_CONFIG.maxDocumentBytes + 1);

    const error = await errorOf(handleStartAiJob(req(t.host, { kind: KIND, request: tooBig }), dispatch, NOW));

    expect(error.details).toMatchObject({ code: "document_too_large" });
    expect(dispatched).toEqual([]);
  });

  it.each([
    ["an unknown kind", { kind: 99, request: "AA==" }],
    ["no request", { kind: KIND }],
    ["an oversized request", { kind: KIND, request: Buffer.alloc(512 * 1024 + 1).toString("base64") }],
    ["an empty request", { kind: KIND, request: "" }],
    [
      "a request naming no Thing",
      {
        kind: KIND,
        request: Buffer.from(
          SuggestTasksRequest.encode(SuggestTasksRequest.fromPartial({ entryPoint: "overview" })).finish(),
        ).toString("base64"),
      },
    ],
  ])("refuses %s", async (_name, data) => {
    expect((await errorOf(handleStartAiJob(req("u", data), recorder().dispatch, NOW))).code).toBe("invalid-argument");
  });
});

describe("getAiEligibility", () => {
  it("offers documents on task suggestions to a Pro owner (T21)", async () => {
    const t = ids();
    await seedSharedThing(t);
    await makePro(t.host);
    expect(await eligibility(t.host, t)).toMatchObject({ allowed: true, documentsAllowed: true });
  });

  it("allows a member and says whether documents are open", async () => {
    const t = ids();
    await seedSharedThing(t);
    expect(await eligibility(t.member, t)).toEqual({
      allowed: true,
      reason: null,
      documentsAllowed: false,
      nextAvailableAt: null,
    });
  });

  it("answers refusals rather than throwing them", async () => {
    const t = ids();
    await seedSharedThing(t);
    expect(await eligibility(t.member, t, "anonymous")).toMatchObject({ allowed: false, reason: "sign_in_required" });
    expect(await eligibility(`x-${randomUUID()}`, t)).toMatchObject({ allowed: false, reason: "not_member" });
  });

  it("reports another member's run in flight, but not the caller's own", async () => {
    const t = ids();
    await seedSharedThing(t);
    await start(t.host, t);
    expect(await eligibility(t.member, t)).toMatchObject({ allowed: false, reason: "run_in_progress" });
    expect(await eligibility(t.host, t)).toMatchObject({ allowed: true });
  });

  it("ignores a stale run", async () => {
    const t = ids();
    await seedSharedThing(t);
    await start(t.host, t, recorder().dispatch, new Date(NOW.getTime() - AI_JOB_STALE_MS - MINUTE));
    expect(await eligibility(t.member, t)).toMatchObject({ allowed: true });
  });
});

describe("closeAiJob", () => {
  it("deletes the caller's job and its input, and is idempotent", async () => {
    const t = ids();
    await seedSharedThing(t);
    const { jobId } = await start(t.host, t);

    expect(await handleCloseAiJob(req(t.host, { jobId }))).toEqual({ closed: true });
    expect((await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).exists).toBe(false);
    expect((await adminDb.doc(aiJobInputDocPath(t.host, jobId)).get()).exists).toBe(false);
    expect(await handleCloseAiJob(req(t.host, { jobId }))).toEqual({ closed: true });
  });

  it("can only reach the caller's own jobs", async () => {
    const t = ids();
    await seedSharedThing(t);
    const { jobId } = await start(t.host, t);

    await handleCloseAiJob(req(t.member, { jobId }));
    expect((await adminDb.doc(aiJobDocPath(t.host, jobId)).get()).exists).toBe(true);
    expect((await errorOf(handleCloseAiJob(req(t.member, { jobId: `../../${t.host}/job/${jobId}` })))).code).toBe(
      "invalid-argument",
    );
  });
});
