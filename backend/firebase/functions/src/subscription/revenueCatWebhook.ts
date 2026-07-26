import { logger } from "firebase-functions/v2";
import { onRequest } from "firebase-functions/v2/https";

import { FUNCTION_REGION, REVENUECAT_WEBHOOK_AUTH } from "../config/env.js";
import { applyEntitlement } from "./applyEntitlement.js";
import {
  normalizeRevenueCatEvent,
  type RevenueCatEvent,
  type RevenueCatWebhookBody,
} from "./revenueCatEntitlementSource.js";

/**
 * RevenueCat's webhook — the real store ingest for subscription_design.html §7 P7.
 *
 * The client never self-grants: a purchase becomes an entitlement only here, after RevenueCat has
 * validated the receipt with Apple/Google server-side. This endpoint normalizes the event and hands
 * it to the one writer of `subscriptions/{uid}`, which is what makes the resulting Pro tier
 * un-forgeable and visible on every platform (including web, which cannot purchase at all).
 *
 * ## Configuration
 *
 * RevenueCat dashboard → Integrations → Webhooks:
 * - **URL**: `https://<region>-<project>.cloudfunctions.net/revenueCatWebhook`
 * - **Authorization header**: a long random string, stored as the `REVENUECAT_WEBHOOK_AUTH` secret
 *   (`firebase functions:secrets:set REVENUECAT_WEBHOOK_AUTH`).
 *
 * That header is the *only* thing standing between the open internet and a function that grants paid
 * entitlements — RevenueCat does not sign its webhook bodies, so there is nothing else to verify
 * against. Hence: App Check is off (RevenueCat is a server, not our app), the comparison is
 * length-safe, and a missing secret fails closed.
 *
 * ## Status codes
 *
 * 200 for anything we deliberately ignore (paywall events, other entitlements, malformed bodies) so
 * RevenueCat stops retrying. 401 for a bad header. 500 only for a genuine write failure, which
 * RevenueCat *should* retry — `applyEntitlement` is idempotent on the event id, so a retried event
 * cannot double-apply.
 */
export const revenueCatWebhook = onRequest(
  {
    region: FUNCTION_REGION,
    secrets: [REVENUECAT_WEBHOOK_AUTH],
    // Note there is no App Check here — unlike every callable in this codebase. `onRequest` has no
    // such option, and RevenueCat's servers could not present a token anyway. The Authorization
    // header checked below is the entire authentication story for this endpoint.
    cors: false,
  },
  async (request, response): Promise<void> => {
    if (request.method !== "POST") {
      response.status(405).send("Method Not Allowed");
      return;
    }

    if (!isAuthorized(request.get("Authorization"))) {
      // No detail in the body — an unauthenticated caller learns nothing about why it failed.
      logger.warn("Rejected RevenueCat webhook with a bad Authorization header");
      response.status(401).send("Unauthorized");
      return;
    }

    const event = extractEvent(request.body);
    if (event == null) {
      logger.warn("Ignoring RevenueCat webhook with no event body");
      response.status(200).json({ applied: false, reason: "malformed" });
      return;
    }

    const result = normalizeRevenueCatEvent(event);
    if (result.kind === "ignored") {
      // `anonymous_app_user_id` is the one that matters operationally: the pilot was charged but the
      // purchase carries no Firebase uid, so no entitlement can ever be written for it. That means
      // the client failed to call `logIn(uid)` before purchasing — see BillingIdentityCoordinator.
      const log = result.reason === "anonymous_app_user_id" ? logger.error : logger.info;
      log("Ignored RevenueCat event", { reason: result.reason, type: event.type });
      response.status(200).json({ applied: false, reason: result.reason });
      return;
    }

    try {
      const { applied } = await applyEntitlement(result.entitlement);
      response.status(200).json({ applied });
    } catch (error) {
      // 500 so RevenueCat retries; the event id keeps the retry idempotent.
      logger.error("Failed to apply RevenueCat entitlement", { error, type: event.type });
      response.status(500).json({ applied: false, reason: "write_failed" });
    }
  },
);

/**
 * Constant-time-ish comparison of the shared secret.
 *
 * Length is checked first and the loop always runs to completion over the expected length, so the
 * time taken does not leak how many characters matched.
 */
function isAuthorized(header: string | undefined): boolean {
  const expected = REVENUECAT_WEBHOOK_AUTH.value()?.trim();
  if (expected == null || expected.length === 0) {
    // Fail closed: an unset secret must never mean "allow everyone".
    logger.error("REVENUECAT_WEBHOOK_AUTH is not set; rejecting all webhook deliveries.");
    return false;
  }
  const actual = header?.trim() ?? "";
  if (actual.length !== expected.length) return false;

  let mismatch = 0;
  for (let i = 0; i < expected.length; i++) {
    mismatch |= expected.charCodeAt(i) ^ actual.charCodeAt(i);
  }
  return mismatch === 0;
}

/** Pulls the `event` object out of the `{ api_version, event }` envelope. */
function extractEvent(body: unknown): RevenueCatEvent | null {
  if (body == null || typeof body !== "object") return null;
  const { event } = body as RevenueCatWebhookBody;
  if (event == null || typeof event !== "object") return null;
  return event as RevenueCatEvent;
}
