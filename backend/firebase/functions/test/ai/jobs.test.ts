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

function encoded({ host, thing }: Ids, documents = 0, templateId = ""): string {
  const request = SuggestTasksRequest.fromPartial({
    thingId: { value: thing },
    hostUid: { value: host },
    documents: Array.from({ length: documents }, (_, i) => ({ name: `doc-${i}.pdf` })),
    context: { templateId: { value: templateId } },
  });
  return Buffer.from(SuggestTasksRequest.encode(request).finish()).toString("base64");
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

    expect(await start(t.host, t, dispatch)).toEqual({ jobId: first.jobId, joined: true });
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
    await adminDb.doc(aiUsageDocPath(t.host, t.thing)).set({
      lastSuccessAt: Timestamp.fromMillis(NOW.getTime() - 60 * MINUTE),
      inFlightJob: null,
    });
    expect((await errorOf(start(t.host, t))).details).toMatchObject({ code: "daily_limit" });
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
    // The cap is the config's, for any kind that takes documents; echo does, task suggestions do
    // not until T21.
    const withDocs = (t: Ids, n: number) =>
      handleStartAiJob(
        req(t.host, { kind: AiJobKind.AI_JOB_KIND_ECHO, request: encoded(t, n) }),
        recorder().dispatch,
        NOW,
      );

    expect((await withDocs(allowed, DEFAULT_AI_CONFIG.maxDocumentsPerRun)).joined).toBe(false);
    expect((await errorOf(withDocs(over, DEFAULT_AI_CONFIG.maxDocumentsPerRun + 1))).code).toBe("invalid-argument");
  });

  it("refuses documents on task suggestions until document runs are wired (T21)", async () => {
    const t = ids();
    await seedSharedThing(t);
    const call = handleStartAiJob(req(t.host, { kind: KIND, request: encoded(t, 1) }), recorder().dispatch, NOW);
    expect((await errorOf(call)).code).toBe("invalid-argument");
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
  it("offers no documents on task suggestions yet, even to a Pro owner", async () => {
    const t = ids();
    await seedSharedThing(t);
    await adminDb.doc(subscriptionDocPath(t.host)).set({
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      willRenew: true,
      currentPeriodEndMillis: NOW.getTime() + 30 * 24 * 60 * MINUTE,
    });
    expect(await eligibility(t.host, t)).toMatchObject({ allowed: true, documentsAllowed: false });
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
