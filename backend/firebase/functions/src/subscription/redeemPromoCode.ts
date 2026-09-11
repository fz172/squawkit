import { logger } from "firebase-functions/v2";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { createAttemptLimiter } from "../shared/attemptLimiter.js";
import { applyEntitlement } from "./applyEntitlement.js";
import {
  effectiveStatusAt,
  isCompedEntitlement,
  subscriptionDocPath,
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  type NormalizedEntitlement,
} from "./entitlementModel.js";
import {
  normalizePromoCode,
  promoCodeDocPath,
  PROMO_ATTEMPTS_COLLECTION,
  type PromoCodeDoc,
} from "./promoCodes.js";

type RedeemRequest = { code: string };
type RedeemResponse = {
  /** The term the code was worth, so the client can say "1 year of Pro" without decoding dates. */
  durationDays: number;
  /** When the granted Pro lapses. Already includes any comp time the account still had. */
  currentPeriodEndMillis: number;
};

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * A promo code carries ~59 bits, which is plenty against a blind guesser but not against an
 * unmetered one with a pool to aim at. Same shape as the invite limiter, its own budget: a pilot who
 * mistyped an invite code this morning must still be able to redeem the code on their conference
 * badge this afternoon.
 */
const promoAttemptLimiter = createAttemptLimiter({
  collection: PROMO_ATTEMPTS_COLLECTION,
  maxFailures: 10,
  windowMs: 60 * 60 * 1000,
  message: "Too many invalid promo codes. Try again later.",
});

/**
 * Redeems a promo code for a term of SquawkIt Pro (#750).
 *
 * The caller sends **only the code**. Its term, its expiry and whether it has been spent all live in
 * the code document, which no client can read — so there is nothing here for a client to assert, and
 * the entitlement is still written by the one writer (`applyEntitlement`) that writes every other
 * entitlement. A promo code is a *request* to grant, exactly as a purchase is.
 *
 * ## Claim, then grant — and release if the grant fails
 *
 * The code is claimed in a transaction and the entitlement written after it, because
 * `applyEntitlement` runs a transaction of its own and Firestore does not nest them. Claiming first
 * is the safe order: two devices racing one code cannot both win, and the failure mode of the
 * second step is recoverable. If the grant throws, the claim is released so the pilot's code is not
 * silently consumed by our outage; if the *release* also fails, the code is left claimed by this uid,
 * which replays as a success on the next attempt rather than as a loss.
 *
 * ## Stacking, and the one case that is refused
 *
 * A code redeemed against an account that still holds comp time extends it — the new term is added
 * to the existing end date, not substituted for it, so redeeming two codes gives two terms.
 *
 * An account on a live **store** subscription is refused instead, without spending the code. Writing
 * a comp over a paying subscriber would replace their `source` and `willRenew`, and the status page
 * would then promise them a lapse date while the store carries on billing. Told plainly, they can
 * redeem the code later; written silently, they would have to notice.
 */
export const redeemPromoCode = onCall<RedeemRequest, Promise<RedeemResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<RedeemResponse> => {
    const { uid } = requireAuthenticatedApp(request);
    requireNonAnonymous(request);
    const code = parseRequest(request.data);

    // Checked BEFORE dereferencing: a locked-out caller must learn nothing about whether the code
    // exists.
    await promoAttemptLimiter.requireAttemptsRemaining(uid);

    const claim = await claimCode(code, uid);
    if (!claim.ok) {
      if (claim.reason === "already_pro") {
        // A legitimate code against a subscribed account — not a guess, so it costs no budget and
        // the code stays unspent.
        throw new HttpsError(
          "failed-precondition",
          "This account already has an active subscription. The promo code has not been used.",
        );
      }
      await promoAttemptLimiter.recordFailedAttempt(uid);
      // "Already used", "expired" and "never existed" deliberately collapse: distinguishing them
      // would confirm to a guesser which codes are real.
      throw new HttpsError("not-found", "This promo code is not valid.");
    }

    const grant: NormalizedEntitlement = {
      uid,
      // One event per CODE, not per invocation: a retry of the same redemption dedups instead of
      // stacking a second term onto the account.
      eventId: `promo-code:${code}`,
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      memberSinceMillis: claim.redeemedAtMillis,
      currentPeriodEndMillis: claim.grantedUntilMillis,
      // A promo does not renew; the client's lifecycle resolution lets it lapse on the end date.
      willRenew: false,
      source: ENTITLEMENT_SOURCE.SERVER_GRANT,
      // In the proto's closed vocabulary (subscription.proto §origin_platform). Both the client's
      // `isCompedEntitlement` and the status page already read it as "granted, nothing to manage".
      originPlatform: "promotional",
    };

    try {
      await applyEntitlement(grant);
    } catch (error) {
      await releaseClaim(code, uid);
      logger.error("Promo grant failed; claim released", { uid, error });
      throw new HttpsError("internal", "Could not apply this promo code. Please try again.");
    }

    logger.info("Redeemed promo code", {
      uid,
      durationDays: claim.durationDays,
      grantedUntilMillis: claim.grantedUntilMillis,
      replay: claim.replay,
    });
    return {
      durationDays: claim.durationDays,
      currentPeriodEndMillis: claim.grantedUntilMillis,
    };
  },
);

type ClaimResult =
  | {
      ok: true;
      durationDays: number;
      redeemedAtMillis: number;
      grantedUntilMillis: number;
      /** The code was already claimed by this same uid — a retry, not a second grant. */
      replay: boolean;
    }
  | { ok: false; reason: "invalid" | "already_pro" };

/**
 * Marks the code spent by [uid] and records what it was worth, in one transaction.
 *
 * The end date is computed and **stored here** rather than derived at grant time, so a replay
 * returns the same date the first attempt did instead of sliding forward by however long the retry
 * took. It is also what makes the spent pool self-describing: the row says what was given away.
 */
async function claimCode(code: string, uid: string): Promise<ClaimResult> {
  const codeRef = adminDb.doc(promoCodeDocPath(code));
  const subRef = adminDb.doc(subscriptionDocPath(uid));

  return adminDb.runTransaction<ClaimResult>(async (tx) => {
    const [codeSnap, subSnap] = await Promise.all([tx.get(codeRef), tx.get(subRef)]);
    if (!codeSnap.exists) return { ok: false, reason: "invalid" };

    const promo = codeSnap.data() as PromoCodeDoc;
    const now = Date.now();

    if (promo.redeemedByUid != null && promo.redeemedByUid !== uid) {
      return { ok: false, reason: "invalid" };
    }
    // An expiry only bars a FIRST claim. A code this uid already spent replays even afterwards —
    // they hold the entitlement, and refusing the retry would strand them on a dropped response.
    if (promo.redeemedByUid == null && promo.expiresAtMillis > 0 && promo.expiresAtMillis <= now) {
      return { ok: false, reason: "invalid" };
    }

    const sub = subSnap.data();
    const isPro = effectiveStatusAt(sub, now) === SUBSCRIPTION_STATUS.PRO;
    // The same comp test the client uses to decide whether to offer the entry at all, so the page
    // never shows a control this refuses.
    const isComp = isCompedEntitlement(sub);
    if (isPro && !isComp) return { ok: false, reason: "already_pro" };

    if (promo.redeemedByUid === uid) {
      return {
        ok: true,
        durationDays: promo.durationDays,
        redeemedAtMillis: promo.redeemedAtMillis ?? now,
        // Older rows minted before the field existed fall back to the term from the claim instant.
        grantedUntilMillis:
          promo.grantedUntilMillis ?? now + promo.durationDays * MS_PER_DAY,
        replay: true,
      };
    }

    // Stack onto comp time the account still holds, rather than truncating it to the new term.
    const existingEnd = isPro && isComp ? numberField(sub?.currentPeriodEndMillis) : 0;
    const base = Math.max(existingEnd, now);
    const grantedUntilMillis = base + promo.durationDays * MS_PER_DAY;

    tx.update(codeRef, {
      redeemedByUid: uid,
      redeemedAtMillis: now,
      grantedUntilMillis,
    });
    return {
      ok: true,
      durationDays: promo.durationDays,
      redeemedAtMillis: now,
      grantedUntilMillis,
      replay: false,
    };
  });
}

/**
 * Puts a claimed code back in the pool after a failed grant.
 *
 * Conditional on the claim still being ours, so a release can never un-spend a code some other
 * account legitimately holds. Best-effort: a failure here leaves the code claimed by this uid, which
 * the replay path above turns back into a successful retry.
 */
async function releaseClaim(code: string, uid: string): Promise<void> {
  const codeRef = adminDb.doc(promoCodeDocPath(code));
  try {
    await adminDb.runTransaction(async (tx) => {
      const snap = await tx.get(codeRef);
      if (!snap.exists) return;
      if ((snap.data() as PromoCodeDoc).redeemedByUid !== uid) return;
      tx.update(codeRef, {
        redeemedByUid: null,
        redeemedAtMillis: null,
        grantedUntilMillis: null,
      });
    });
  } catch (error) {
    logger.error("Could not release promo claim", { uid, error });
  }
}

/**
 * A guest account cannot be recovered on another device or after a reinstall, so it must not hold an
 * entitlement — the same rule the subscribe button enforces, and the reason a promo code spent by a
 * guest would be a code thrown away.
 */
function requireNonAnonymous(request: CallableRequest<unknown>): void {
  if (request.auth?.token?.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("permission-denied", "Sign in before redeeming a promo code.");
  }
}

/** Accepts what a human types: lowercase, spaces, and the displayed `PRQK-8H3M-XTVB` grouping. */
function parseRequest(data: unknown): string {
  const obj = (data ?? {}) as Record<string, unknown>;
  const raw = typeof obj.code === "string" ? obj.code : "";
  const code = normalizePromoCode(raw);
  if (code.length === 0) {
    throw new HttpsError("invalid-argument", "A promo code is required.");
  }
  return code;
}

function numberField(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
