import { logger } from "firebase-functions/v2";

import { adminDb } from "../config/firebaseAdmin.js";
import {
  entitlementIngestDocPath,
  subscriptionDocPath,
  type NormalizedEntitlement,
} from "./entitlementModel.js";

export type ApplyResult = {
  applied: boolean;
  /** Why an apply was skipped, for logging/tests. Absent when `applied` is true. */
  reason?: "duplicate";
};

/**
 * The single writer of the entitlement fields on `subscriptions/{uid}` (subscription_design.html §7).
 *
 * Idempotent, keyed on the provider's transaction/event id: a marker doc at
 * `entitlement_ingest/{eventId}` records that an event was applied, so a retry or a re-delivery of
 * the same event is a no-op instead of a second write. The check-and-write runs in one transaction
 * (reading only the marker), so two concurrent deliveries of the same event can't both slip through.
 *
 * It writes ONLY the entitlement fields, via `merge`, so it never disturbs the storage-usage sweep's
 * `storageBytesUsed` on the same doc — the two writers own disjoint fields and compose server-side.
 *
 * NOTE: this dedups re-delivery, not out-of-order delivery between DIFFERENT events. A real provider
 * whose events can arrive out of order will add an ordering guard when it lands (P7); the v1 sources
 * (manual server grants) issue one event per grant, so there is nothing to reorder yet.
 */
export async function applyEntitlement(update: NormalizedEntitlement): Promise<ApplyResult> {
  const subRef = adminDb.doc(subscriptionDocPath(update.uid));
  const markerRef = adminDb.doc(entitlementIngestDocPath(update.eventId));

  const result = await adminDb.runTransaction<ApplyResult>(async (tx) => {
    const marker = await tx.get(markerRef);
    if (marker.exists) {
      return { applied: false, reason: "duplicate" };
    }

    tx.set(
      subRef,
      {
        status: update.status,
        lifecycle: update.lifecycle,
        memberSinceMillis: update.memberSinceMillis,
        currentPeriodEndMillis: update.currentPeriodEndMillis,
        willRenew: update.willRenew,
        source: update.source,
        originPlatform: update.originPlatform,
      },
      { merge: true },
    );
    tx.set(markerRef, { uid: update.uid, appliedAtMillis: Date.now() });
    return { applied: true };
  });

  if (result.applied) {
    logger.info("Applied entitlement", { uid: update.uid, eventId: update.eventId, source: update.source });
  } else {
    logger.info("Skipped duplicate entitlement event", { uid: update.uid, eventId: update.eventId });
  }
  return result;
}
