import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

import {
  ENTITLEMENT_RECONCILE_ON_DEMAND_THROTTLE_MS,
  FUNCTION_REGION,
  REVENUECAT_SECRET_API_KEY,
} from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { applyEntitlement } from "./applyEntitlement.js";
import { reconcileThrottleDocPath } from "./entitlementModel.js";
import { fetchSubscriber, RevenueCatRateLimitError } from "./revenueCatApi.js";
import { normalizeSubscriber } from "./revenueCatSubscriberSource.js";

type ReconcileResponse = {
  /** Whether an entitlement write actually happened. */
  reconciled: boolean;
  /** Why nothing was done, when nothing was done. Diagnostics only; the client ignores it. */
  reason?: "throttled" | "no_customer" | "unchanged" | "unavailable";
};

/**
 * On-demand entitlement reconcile for the **calling account only** (#355).
 *
 * The daily scan cannot catch the case that matters most to a paying customer: someone who has just
 * paid and whose webhook never arrived. Such an account holds no Pro entitlement, so there is
 * nothing stale for a scan to notice — and the person is watching the screen right now. This is the
 * counterpart trigger, called by the client when a purchase has completed at the store but the
 * entitlement has not synced down within a few seconds.
 *
 * ## Why this cannot be used to grant yourself Pro
 *
 * The client supplies **nothing**. The uid comes from `request.auth`, never from the payload, so the
 * call means only "re-check me" — never "make me Pro", and never "go look at someone else". The
 * server then asks RevenueCat with its own secret key and writes through the same single writer.
 * Forging the call gets you a lookup that tells the truth about your own account.
 *
 * ## Throttling
 *
 * Rate-limited per account, because a client can call this as often as it likes and every call
 * costs a RevenueCat REST request against a rate-limited API. The throttle marker lives in its own
 * collection rather than on `subscriptions/{uid}`, for the same reason `entitlement_ingest` does:
 * the client decodes that document, and server bookkeeping has no business in it.
 */
export const reconcileMyEntitlement = onCall<unknown, Promise<ReconcileResponse>>(
  {
    region: FUNCTION_REGION,
    enforceAppCheck: true,
    secrets: [REVENUECAT_SECRET_API_KEY],
  },
  async (request): Promise<ReconcileResponse> => {
    if (request.auth == null) {
      throw new HttpsError("unauthenticated", "Sign-in required.");
    }
    const uid = request.auth.uid;

    const apiKey = REVENUECAT_SECRET_API_KEY.value()?.trim();
    if (apiKey == null || apiKey.length === 0) {
      logger.error("REVENUECAT_SECRET_API_KEY is not set; cannot reconcile on demand.");
      return { reconciled: false, reason: "unavailable" };
    }

    const now = Date.now();
    if (!(await claimThrottleSlot(uid, now))) {
      // Visible because from the client's side a throttled call and a call that never happened look
      // identical — both are "I opened the page and nothing changed".
      logger.info("On-demand reconcile throttled", { uid });
      return { reconciled: false, reason: "throttled" };
    }

    try {
      const subscriber = await fetchSubscriber(uid, apiKey);
      if (subscriber == null) {
        // RevenueCat has no record of this account. For an on-demand call that is the *expected*
        // answer for anyone who has never purchased, so unlike the scan it is not alarming — and it
        // still must not write anything.
        return { reconciled: false, reason: "no_customer" };
      }

      const resolved = normalizeSubscriber(uid, subscriber, now);
      const { applied } = await applyEntitlement(resolved);
      // Logged on both branches, and always carrying the management URL: the client calls this
      // specifically to learn that URL (#363), so "was it applied, and what URL did we resolve" is
      // the whole diagnostic. An "unchanged" here with an empty URL is the expected, correct outcome
      // for a Test Store purchase — the same shape as a genuine failure, so it has to be visible.
      logger.info("On-demand reconcile finished", {
        uid,
        applied,
        status: resolved.status,
        lifecycle: resolved.lifecycle,
        currentPeriodEndMillis: resolved.currentPeriodEndMillis,
        managementUrl: resolved.managementUrl ?? "",
        eventId: resolved.eventId,
      });
      return applied ? { reconciled: true } : { reconciled: false, reason: "unchanged" };
    } catch (error) {
      if (error instanceof RevenueCatRateLimitError) {
        logger.warn("On-demand reconcile hit the RevenueCat rate limit", { uid });
        return { reconciled: false, reason: "unavailable" };
      }
      // Never surface an error to the caller: the client treats this as a hint, and a failed
      // reconcile must not turn into a visible error on top of a purchase that already succeeded.
      logger.error("On-demand reconcile failed", { uid, error });
      return { reconciled: false, reason: "unavailable" };
    }
  },
);

/**
 * Takes the account's reconcile slot, or reports that it was taken recently.
 *
 * A transaction rather than read-then-write: two launches racing (or a client retrying) would
 * otherwise both see a stale timestamp and both call RevenueCat.
 */
async function claimThrottleSlot(uid: string, nowMillis: number): Promise<boolean> {
  const ref = adminDb.doc(reconcileThrottleDocPath(uid));
  return adminDb.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    const last = snapshot.data()?.lastReconciledAtMillis;
    const lastMillis = typeof last === "number" ? last : 0;
    if (nowMillis - lastMillis < ENTITLEMENT_RECONCILE_ON_DEMAND_THROTTLE_MS) return false;
    tx.set(ref, { lastReconciledAtMillis: nowMillis });
    return true;
  });
}
