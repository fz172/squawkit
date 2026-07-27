import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";

import {
  ENTITLEMENT_RECONCILE_GRACE_MS,
  ENTITLEMENT_RECONCILE_MAX_PER_RUN,
  ENTITLEMENT_RECONCILE_REQUEST_SPACING_MS,
  ENTITLEMENT_RECONCILE_SCHEDULE,
  FUNCTION_REGION,
  REVENUECAT_SECRET_API_KEY,
} from "../config/env.js";
import { runEntitlementReconcile } from "./reconcileEntitlements.js";

/**
 * The entitlement reconciler on a timer (#355).
 *
 * `onSchedule` provisions a Cloud Scheduler job, which is also how to run it on demand — the job has
 * a "Force run" button in the GCP console. Scheduler invokes it as a trusted caller, so there is no
 * endpoint to expose and no admin auth to design, and nothing on the internet can ask us to go
 * rewrite entitlements.
 *
 * Timeout is generous because the run paces itself against RevenueCat's rate limit (roughly one
 * request per second) — 200 accounts is a few minutes of mostly waiting. Nothing is blocked on it.
 */
export const scheduledEntitlementReconcile = onSchedule(
  {
    schedule: ENTITLEMENT_RECONCILE_SCHEDULE,
    region: FUNCTION_REGION,
    secrets: [REVENUECAT_SECRET_API_KEY],
    timeoutSeconds: 540,
  },
  async () => {
    const apiKey = REVENUECAT_SECRET_API_KEY.value()?.trim();
    if (apiKey == null || apiKey.length === 0) {
      // Fail loudly and do nothing. A reconciler that cannot reach RevenueCat must not guess: every
      // account would look unverifiable, and the only safe response to "I don't know" is to leave
      // the entitlement exactly as it is.
      logger.error("REVENUECAT_SECRET_API_KEY is not set; skipping the entitlement reconcile.");
      return;
    }

    await runEntitlementReconcile({
      apiKey,
      nowMillis: Date.now(),
      graceMs: ENTITLEMENT_RECONCILE_GRACE_MS,
      maxPerRun: ENTITLEMENT_RECONCILE_MAX_PER_RUN,
      requestSpacingMs: ENTITLEMENT_RECONCILE_REQUEST_SPACING_MS,
    });
  },
);
