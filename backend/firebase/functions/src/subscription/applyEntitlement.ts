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

    // "Member since" is the EARLIEST purchase/trial start (design §3), but a provider reports the
    // current transaction's date on every event — a renewal would otherwise walk it forward a year
    // at a time and the status page would tell a two-year subscriber they joined last month.
    const existing = await tx.get(subRef);
    const memberSinceMillis = earliestMemberSince(
      existing.data()?.memberSinceMillis,
      update.memberSinceMillis,
    );

    tx.set(
      subRef,
      {
        status: update.status,
        lifecycle: update.lifecycle,
        memberSinceMillis,
        currentPeriodEndMillis: update.currentPeriodEndMillis,
        willRenew: update.willRenew,
        source: update.source,
        originPlatform: update.originPlatform,
        // Omitted from the merge entirely when the source doesn't know it (#363). The webhook event
        // carries no `management_url`, so writing `""` here would blank the link a reconcile had
        // learned every time a renewal came through.
        ...(update.managementUrl === undefined ? {} : { managementUrl: update.managementUrl }),
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

/**
 * The earlier of the stored and incoming member-since, ignoring absent/zero on either side.
 *
 * A server grant with no known start (0) must not clobber a real purchase date, and a real purchase
 * date must not be pushed later by a subsequent event.
 */
function earliestMemberSince(stored: unknown, incoming: number): number {
  const storedMillis = typeof stored === "number" && stored > 0 ? stored : 0;
  const incomingMillis = incoming > 0 ? incoming : 0;
  if (storedMillis === 0) return incomingMillis;
  if (incomingMillis === 0) return storedMillis;
  return Math.min(storedMillis, incomingMillis);
}
