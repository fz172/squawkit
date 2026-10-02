import { randomUUID } from "node:crypto";

import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { describe, expect, it } from "vitest";

import {
  AI_DAILY_LIMIT_MS,
  authorizeAiCall,
  decideAiAccess,
  startOfNextUtcMonth,
  type AiAccessFacts,
  type AiAccessRequest,
} from "../../src/ai/authorize.js";
import {
  AI_CONFIG_DOC_PATH,
  DEFAULT_AI_CONFIG,
  aiSpendDocPath,
  aiUsageDocPath,
  type AiConfig,
} from "../../src/ai/collections.js";
import { thingShareDocPath } from "../../src/sharing/sharingModels.js";
import {
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  subscriptionDocPath,
} from "../../src/subscription/entitlementModel.js";
import { adminDb, req } from "../helpers.js";

const NOW = new Date("2026-10-15T12:00:00Z");
const HOUR = 60 * 60 * 1000;
const ENABLED: AiConfig = {
  ...DEFAULT_AI_CONFIG,
  enabled: true,
  monthlyCeilingMicros: { free: 100, pro: 200, total: 250 },
};

const request: AiAccessRequest = { callerUid: "u", hostUid: "u", thingId: "t", withDocuments: false };

function facts(overrides: Partial<AiAccessFacts> = {}): AiAccessFacts {
  return { config: ENABLED, isMember: true, ownerTier: "free", usage: null, spend: null, ...overrides };
}

function usedAt(msAgo: number): AiAccessFacts["usage"] {
  return { lastSuccessAt: Timestamp.fromMillis(NOW.getTime() - msAgo), inFlightJob: null };
}

function spent(freeMicros: number, proMicros: number): AiAccessFacts["spend"] {
  return { freeMicros, proMicros, updatedAt: Timestamp.fromDate(NOW) };
}

describe("decideAiAccess", () => {
  it("allows a member of an enabled backend with nothing used", () => {
    expect(decideAiAccess(request, facts(), NOW)).toMatchObject({ allowed: true, ownerTier: "free" });
  });

  it.each<[string, AiAccessRequest, AiAccessFacts, string]>([
    ["the kill switch is off", request, facts({ config: { ...ENABLED, enabled: false } }), "disabled"],
    ["the caller is not a member", request, facts({ isMember: false }), "not_member"],
    ["documents on a free owner", { ...request, withDocuments: true }, facts(), "owner_not_pro"],
    ["a success 23 h ago", request, facts({ usage: usedAt(23 * HOUR) }), "daily_limit"],
    ["free spend at its ceiling", request, facts({ spend: spent(100, 0) }), "spend_ceiling"],
    ["pro spend at its ceiling", request, facts({ ownerTier: "pro", spend: spent(0, 200) }), "spend_ceiling"],
    ["the project at its total", request, facts({ ownerTier: "pro", spend: spent(90, 160) }), "spend_ceiling"],
  ])("refuses when %s", (_name, req, f, code) => {
    expect(decideAiAccess(req, f, NOW)).toMatchObject({ allowed: false, code });
  });

  it("checks in policy order, so a non-member learns nothing about the Thing", () => {
    const everythingWrong = facts({
      isMember: false,
      usage: usedAt(HOUR),
      spend: spent(1_000, 1_000),
    });
    expect(decideAiAccess({ ...request, withDocuments: true }, everythingWrong, NOW)).toEqual({
      allowed: false,
      code: "not_member",
      nextAvailableAt: null,
      documentsAllowed: false,
    });
  });

  it("puts the kill switch before membership", () => {
    const f = facts({ isMember: false, config: { ...ENABLED, enabled: false } });
    expect(decideAiAccess(request, f, NOW)).toMatchObject({ code: "disabled" });
  });

  it("allows documents on a Pro owner, whatever the member's own tier", () => {
    expect(decideAiAccess({ ...request, withDocuments: true }, facts({ ownerTier: "pro" }), NOW)).toMatchObject({
      allowed: true,
      documentsAllowed: true,
    });
  });

  it("allows again exactly 24 h after the last success, and says when", () => {
    expect(decideAiAccess(request, facts({ usage: usedAt(AI_DAILY_LIMIT_MS) }), NOW).allowed).toBe(true);
    expect(decideAiAccess(request, facts({ usage: usedAt(AI_DAILY_LIMIT_MS - HOUR) }), NOW)).toMatchObject({
      code: "daily_limit",
      nextAvailableAt: new Date(NOW.getTime() + HOUR),
    });
  });

  it("does not count an in-flight run or a missing success against the limit", () => {
    const usage = { lastSuccessAt: null, inFlightJob: { callerUid: "u", jobId: "j" } };
    expect(decideAiAccess(request, facts({ usage }), NOW).allowed).toBe(true);
  });

  it("charges spend to the owner's tier only", () => {
    expect(decideAiAccess(request, facts({ ownerTier: "free", spend: spent(0, 199) }), NOW).allowed).toBe(true);
  });

  it("resets spend at the next UTC month", () => {
    expect(decideAiAccess(request, facts({ spend: spent(100, 0) }), NOW)).toMatchObject({
      nextAvailableAt: new Date("2026-11-01T00:00:00Z"),
    });
    expect(startOfNextUtcMonth(new Date("2026-12-31T23:00:00Z"))).toEqual(new Date("2027-01-01T00:00:00Z"));
  });

  it("reports whether documents are allowed once membership has passed", () => {
    expect(decideAiAccess(request, facts({ ownerTier: "pro", usage: usedAt(HOUR) }), NOW)).toMatchObject({
      code: "daily_limit",
      documentsAllowed: true,
    });
  });
});

describe("authorizeAiCall (emulator)", () => {
  async function seedConfig(config: AiConfig = ENABLED): Promise<void> {
    await adminDb.doc(AI_CONFIG_DOC_PATH).set(config);
  }

  async function seedThing(hostUid: string, thingId: string, deleted = false): Promise<void> {
    await adminDb.doc(`users/${hostUid}/thing/${thingId}`).set({ deleted, payload: "" });
  }

  async function seedPro(uid: string): Promise<void> {
    await adminDb.doc(subscriptionDocPath(uid)).set({
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      willRenew: true,
      currentPeriodEndMillis: NOW.getTime() + 30 * 24 * HOUR,
    });
  }

  async function codeOf(promise: Promise<unknown>): Promise<string> {
    const error = await promise.then(
      () => null,
      (e: unknown) => e,
    );
    expect(error).toBeInstanceOf(HttpsError);
    return ((error as HttpsError).details as { code: string }).code;
  }

  const ids = () => ({ host: `h-${randomUUID()}`, member: `m-${randomUUID()}`, thing: `t-${randomUUID()}` });

  it("allows the owner of a live Thing", async () => {
    await seedConfig();
    const { host, thing } = ids();
    await seedThing(host, thing);
    const result = await authorizeAiCall(req(host, {}), { hostUid: host, thingId: thing, withDocuments: false }, NOW);
    expect(result).toMatchObject({ allowed: true, callerUid: host, ownerTier: "free" });
  });

  it("refuses a guest before anything else", async () => {
    const { host, thing } = ids();
    const call = authorizeAiCall(req(host, {}, "anonymous"), { hostUid: host, thingId: thing, withDocuments: false }, NOW);
    expect(await codeOf(call)).toBe("sign_in_required");
  });

  it("refuses an invented or tombstoned Thing, which would otherwise be a fresh daily limit", async () => {
    await seedConfig();
    const { host, thing } = ids();
    expect(
      await codeOf(authorizeAiCall(req(host, {}), { hostUid: host, thingId: thing, withDocuments: false }, NOW)),
    ).toBe("not_member");
    await seedThing(host, thing, true);
    expect(
      await codeOf(authorizeAiCall(req(host, {}), { hostUid: host, thingId: thing, withDocuments: false }, NOW)),
    ).toBe("not_member");
  });

  it("allows a share technician, judging documents by the owner's tier", async () => {
    await seedConfig();
    const { host, member, thing } = ids();
    await adminDb.doc(thingShareDocPath(host, thing)).set({ memberRoles: { [host]: "owner", [member]: "technician" } });
    expect(
      await codeOf(authorizeAiCall(req(member, {}), { hostUid: host, thingId: thing, withDocuments: true }, NOW)),
    ).toBe("owner_not_pro");
    await seedPro(host);
    const result = await authorizeAiCall(req(member, {}), { hostUid: host, thingId: thing, withDocuments: true }, NOW);
    expect(result).toMatchObject({ allowed: true, callerUid: member, ownerTier: "pro" });
  });

  it("refuses someone outside the share", async () => {
    await seedConfig();
    const { host, member, thing } = ids();
    await seedThing(host, thing);
    expect(
      await codeOf(authorizeAiCall(req(member, {}), { hostUid: host, thingId: thing, withDocuments: false }, NOW)),
    ).toBe("not_member");
  });

  it("reads the daily limit and spend from their documents", async () => {
    await seedConfig();
    const { host, thing } = ids();
    await seedThing(host, thing);
    await adminDb.doc(aiUsageDocPath(host, thing)).set({
      lastSuccessAt: Timestamp.fromMillis(NOW.getTime() - HOUR),
      inFlightJob: null,
    });
    const error = await authorizeAiCall(req(host, {}), { hostUid: host, thingId: thing, withDocuments: false }, NOW).catch(
      (e: HttpsError) => e,
    );
    expect(error).toMatchObject({
      code: "resource-exhausted",
      details: { code: "daily_limit", nextAvailableAt: new Date(NOW.getTime() + 23 * HOUR).toISOString() },
    });

    const other = ids();
    await seedThing(other.host, other.thing);
    await adminDb.doc(aiSpendDocPath(NOW)).set({ freeMicros: 100, proMicros: 0, updatedAt: Timestamp.fromDate(NOW) });
    try {
      expect(
        await codeOf(
          authorizeAiCall(req(other.host, {}), { hostUid: other.host, thingId: other.thing, withDocuments: false }, NOW),
        ),
      ).toBe("spend_ceiling");
    } finally {
      await adminDb.doc(aiSpendDocPath(NOW)).delete();
    }
  });

  it("fails closed with no config at all", async () => {
    await adminDb.doc(AI_CONFIG_DOC_PATH).delete();
    const { host, thing } = ids();
    await seedThing(host, thing);
    expect(
      await codeOf(authorizeAiCall(req(host, {}), { hostUid: host, thingId: thing, withDocuments: false }, NOW)),
    ).toBe("disabled");
  });
});
