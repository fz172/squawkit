import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { requireAdmin } from "../shared/auth.js";
import { applyEntitlement } from "./applyEntitlement.js";
import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  type NormalizedEntitlement,
} from "./entitlementModel.js";

type GrantRequest = { uid: string; durationDays: number };
type GrantResponse = { applied: boolean; currentPeriodEndMillis: number };

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * Admin-only promo grant (subscription_design.html §7): writes a comp'd Pro entitlement with an end
 * date and no store purchase (`SERVER_GRANT`). The client never self-grants from a local receipt —
 * it only reads the synced result — so a comp lives here, behind the admin claim.
 *
 * A grant is one event, so its id makes re-invocation idempotent through `applyEntitlement`. The
 * grant does not auto-renew (`willRenew: false`); when `currentPeriodEndMillis` passes, the client's
 * lifecycle resolution lets it lapse back to Free on its own.
 */
export const grantPromoEntitlement = onCall<GrantRequest, Promise<GrantResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<GrantResponse> => {
    requireAdmin(request);
    const { uid, durationDays } = parseRequest(request.data);

    const now = Date.now();
    const currentPeriodEndMillis = now + durationDays * MS_PER_DAY;
    const grant: NormalizedEntitlement = {
      uid,
      // One event per grant; the timestamp keeps repeated grants to the same uid distinct while a
      // double-fire of the SAME invocation dedups.
      eventId: `promo:${uid}:${now}`,
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      memberSinceMillis: now,
      currentPeriodEndMillis,
      willRenew: false,
      source: ENTITLEMENT_SOURCE.SERVER_GRANT,
      originPlatform: "server",
    };

    const { applied } = await applyEntitlement(grant);
    return { applied, currentPeriodEndMillis };
  },
);

function parseRequest(data: unknown): GrantRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const uid = typeof obj.uid === "string" ? obj.uid.trim() : "";
  const durationDays = typeof obj.durationDays === "number" ? obj.durationDays : NaN;

  if (uid.length === 0) {
    throw new HttpsError("invalid-argument", "A target uid is required.");
  }
  if (!Number.isFinite(durationDays) || durationDays <= 0) {
    throw new HttpsError("invalid-argument", "durationDays must be a positive number.");
  }
  return { uid, durationDays };
}
