import { HttpsError, type CallableRequest, type FunctionsErrorCode } from "firebase-functions/v2/https";

import { ENTITY_SEGMENT_THING, entityDocPath } from "../config/entitySegment.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireSignedInApp } from "../shared/auth.js";
import { isShareMember, loadShare } from "../storage/blobBroker.js";
import {
  SUBSCRIPTION_STATUS,
  effectiveStatusAt,
  subscriptionDocPath,
} from "../subscription/entitlementModel.js";
import {
  AI_CONFIG_DOC_PATH,
  aiSpendDocPath,
  aiUsageDocPath,
  parseAiConfig,
  type AiConfig,
  type AiOwnerTier,
  type AiSpendDoc,
  type AiUsageDoc,
} from "./collections.js";
import type { AiErrorCode } from "./errors.js";

/**
 * Who may start an AI run, and when (docs/ai/task_population_design.md §5.3; PRD R45–R47, R49).
 *
 * `decideAiAccess` is the policy, a pure function over facts so every rule is tested without
 * Firestore; `loadAiAccessFacts` reads them; `authorizeAiCall` is what a callable runs. The order
 * of the checks is part of the policy: a caller who is not a member learns that and nothing else,
 * never the Thing's usage or the owner's tier.
 *
 * `run_in_progress` (R19a) is not here: it is decided in the transaction that starts a run (T08).
 */

/** One successful run per Thing per rolling 24 h, for every tier (PRD decision 16). */
export const AI_DAILY_LIMIT_MS = 24 * 60 * 60 * 1000;

export type AiAccessRequest = {
  callerUid: string;
  /** The Thing's tree. Routing as the client sent it; membership decides whether it is honest. */
  hostUid: string;
  thingId: string;
  withDocuments: boolean;
};

export type AiAccessFacts = {
  config: AiConfig;
  /** The caller owns a live Thing at that path, or is in its share. */
  isMember: boolean;
  ownerTier: AiOwnerTier;
  usage: AiUsageDoc | null;
  spend: AiSpendDoc | null;
};

export type AiAccessDecision =
  | { allowed: true; ownerTier: AiOwnerTier; config: AiConfig; documentsAllowed: boolean }
  | {
      allowed: false;
      code: AiErrorCode;
      /** When asking again can succeed, for `daily_limit` and `spend_ceiling`; else null. */
      nextAvailableAt: Date | null;
      /** Known only once membership has passed; false before it, so a non-member learns nothing. */
      documentsAllowed: boolean;
    };

export function decideAiAccess(
  request: AiAccessRequest,
  facts: AiAccessFacts,
  now: Date,
): AiAccessDecision {
  const deny = (code: AiErrorCode, nextAvailableAt: Date | null = null, documentsAllowed = false) =>
    ({ allowed: false, code, nextAvailableAt, documentsAllowed }) as const;

  if (!facts.config.enabled) return deny("disabled");
  if (!facts.isMember) return deny("not_member");

  // Documents need the OWNER's Pro (R45, R46): a member's own billing never decides it.
  const documentsAllowed = facts.ownerTier === "pro";
  if (request.withDocuments && !documentsAllowed) return deny("owner_not_pro", null, documentsAllowed);

  const lastSuccess = facts.usage?.lastSuccessAt?.toMillis() ?? null;
  if (lastSuccess != null && now.getTime() - lastSuccess < AI_DAILY_LIMIT_MS) {
    return deny("daily_limit", new Date(lastSuccess + AI_DAILY_LIMIT_MS), documentsAllowed);
  }

  const free = facts.spend?.freeMicros ?? 0;
  const pro = facts.spend?.proMicros ?? 0;
  const ceilings = facts.config.monthlyCeilingMicros;
  const tierSpend = facts.ownerTier === "pro" ? pro : free;
  if (tierSpend >= ceilings[facts.ownerTier] || free + pro >= ceilings.total) {
    return deny("spend_ceiling", startOfNextUtcMonth(now), documentsAllowed);
  }

  return { allowed: true, ownerTier: facts.ownerTier, config: facts.config, documentsAllowed };
}

/** Spend resets when `ai_spend`'s month key does, at UTC midnight on the 1st. */
export function startOfNextUtcMonth(now: Date): Date {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
}

export async function loadAiAccessFacts(request: AiAccessRequest, now: Date): Promise<AiAccessFacts> {
  const [configSnap, isMember, ownerTier, usageSnap, spendSnap] = await Promise.all([
    adminDb.doc(AI_CONFIG_DOC_PATH).get(),
    isAiMember(request.callerUid, request.hostUid, request.thingId),
    ownerTierFor(request.hostUid, now),
    adminDb.doc(aiUsageDocPath(request.hostUid, request.thingId)).get(),
    adminDb.doc(aiSpendDocPath(now)).get(),
  ]);
  return {
    config: parseAiConfig(configSnap.data()),
    isMember,
    ownerTier,
    usage: usageSnap.exists ? (usageSnap.data() as AiUsageDoc) : null,
    spend: spendSnap.exists ? (spendSnap.data() as AiSpendDoc) : null,
  };
}

/**
 * Membership for an AI run: the caller owns the Thing, or is in its share in either role (design
 * §2: owner and technician both write tasks).
 *
 * The owner's Thing must exist and not be tombstoned. Without that check an invented thingId would
 * be a fresh daily limit. It does mean a Thing created on a device that has not synced yet is not
 * found, so the client waits for the Thing to sync before it starts a run, as it does for documents.
 */
export async function isAiMember(callerUid: string, hostUid: string, thingId: string): Promise<boolean> {
  if (callerUid === hostUid) {
    const thing = await adminDb.doc(entityDocPath(hostUid, thingId, ENTITY_SEGMENT_THING)).get();
    return thing.exists && thing.get("deleted") !== true;
  }
  const share = await loadShare(hostUid, thingId);
  return share != null && isShareMember(share, callerUid);
}

/** The Thing owner's tier, from their own entitlement: what documents and spend are judged by. */
export async function ownerTierFor(hostUid: string, now: Date): Promise<AiOwnerTier> {
  const snap = await adminDb.doc(subscriptionDocPath(hostUid)).get();
  return effectiveStatusAt(snap.data(), now.getTime()) === SUBSCRIPTION_STATUS.PRO ? "pro" : "free";
}

/** The gRPC status each refusal travels under; the client reads `details.code`, not this. */
const STATUS_FOR: Partial<Record<AiErrorCode, FunctionsErrorCode>> = {
  disabled: "unavailable",
  not_member: "permission-denied",
  owner_not_pro: "failed-precondition",
  daily_limit: "resource-exhausted",
  spend_ceiling: "resource-exhausted",
};

export function aiAccessError(decision: Extract<AiAccessDecision, { allowed: false }>): HttpsError {
  return new HttpsError(STATUS_FOR[decision.code] ?? "failed-precondition", decision.code, {
    code: decision.code,
    nextAvailableAt: decision.nextAvailableAt?.toISOString() ?? null,
  });
}

/**
 * Refusals a run can step around by returning only the curated suggestions (design §5.1, PRD R9a):
 * they protect model spend, and curated suggestions cost none. Every other refusal stands.
 */
export const CURATED_FALLBACK_CODES: ReadonlySet<AiErrorCode> = new Set(["disabled", "daily_limit", "spend_ceiling"]);

/** Why a curated-only run's AI did not run, and when it can, for `daily_limit` and `spend_ceiling`. */
export type AiSkipped = { code: AiErrorCode; nextAvailableAt: Date | null };

/** What `startAiJob` does: run the model, return the curated suggestions alone, or refuse. */
export type AiStartPlan =
  | { run: "ai"; access: Extract<AiAccessDecision, { allowed: true }> }
  | { run: "curated"; skipped: AiSkipped | null }
  | { run: "refused"; decision: Extract<AiAccessDecision, { allowed: false }> };

/**
 * The start policy over `decideAiAccess`'s answer. A request for curated suggestions only, or one
 * the model is refused for a `CURATED_FALLBACK_CODES` reason, returns the curated list, but only to
 * a member: `disabled` is decided before membership, so it is checked here again. `skipped` says
 * why the model did not run; it is null when only curated suggestions were asked for.
 */
export function planAiStart(
  decision: AiAccessDecision,
  isMember: boolean,
  request: { curatedOnly: boolean; offersCuratedOnly: boolean },
): AiStartPlan {
  const fallback = !decision.allowed && CURATED_FALLBACK_CODES.has(decision.code);
  if (request.offersCuratedOnly && (request.curatedOnly || fallback)) {
    if (!isMember) {
      return { run: "refused", decision: { allowed: false, code: "not_member", nextAvailableAt: null, documentsAllowed: false } };
    }
    if (request.curatedOnly) return { run: "curated", skipped: null };
    const denied = decision as Extract<AiAccessDecision, { allowed: false }>;
    return { run: "curated", skipped: { code: denied.code, nextAvailableAt: denied.nextAvailableAt } };
  }
  return decision.allowed ? { run: "ai", access: decision } : { run: "refused", decision };
}

/** `authorizeAiCall` for `startAiJob`: the same checks, planned by `planAiStart`. */
export async function authorizeAiStart(
  request: CallableRequest<unknown>,
  target: { hostUid: string; thingId: string; withDocuments: boolean; curatedOnly: boolean; offersCuratedOnly: boolean },
  now: Date = new Date(),
): Promise<Exclude<AiStartPlan, { run: "refused" }> & { callerUid: string }> {
  const { uid } = requireSignedInApp(request);
  const access: AiAccessRequest = { callerUid: uid, hostUid: target.hostUid, thingId: target.thingId, withDocuments: target.withDocuments };
  const facts = await loadAiAccessFacts(access, now);
  const plan = planAiStart(decideAiAccess(access, facts, now), facts.isMember, target);
  if (plan.run === "refused") throw aiAccessError(plan.decision);
  return { ...plan, callerUid: uid };
}

/**
 * The checks every AI callable runs, in order: signed in with an allowed app and not a guest, then
 * `decideAiAccess`. Throws an HttpsError whose `details.code` is the §5.7 code.
 */
export async function authorizeAiCall(
  request: CallableRequest<unknown>,
  target: { hostUid: string; thingId: string; withDocuments: boolean },
  now: Date = new Date(),
): Promise<Extract<AiAccessDecision, { allowed: true }> & { callerUid: string }> {
  const { uid } = requireSignedInApp(request);
  const access: AiAccessRequest = { callerUid: uid, ...target };
  const decision = decideAiAccess(access, await loadAiAccessFacts(access, now), now);
  if (!decision.allowed) throw aiAccessError(decision);
  return { ...decision, callerUid: uid };
}
