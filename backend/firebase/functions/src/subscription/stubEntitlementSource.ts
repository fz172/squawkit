import { applyEntitlement, type ApplyResult } from "./applyEntitlement.js";
import { ENTITLEMENT_SOURCE, type NormalizedEntitlement } from "./entitlementModel.js";

/**
 * The stub provider ingest (subscription_design.html §7): the placeholder `EntitlementSource` that
 * stands in for a real store notification / aggregator webhook until the billing provider is chosen.
 *
 * It validates no receipt or signature — that is the real provider's job in P7 — and exists so the
 * whole pipeline (normalize → `applyEntitlement` → sync → client) can be exercised end to end now,
 * alongside manual promo grants. When a real provider lands it replaces this normalize step; the
 * writer it feeds does not change.
 */
export type StubProviderEvent = {
  /** Provider transaction/event id — carried through as the idempotency key. */
  eventId: string;
  uid: string;
  status: number;
  lifecycle: number;
  memberSinceMillis: number;
  currentPeriodEndMillis: number;
  willRenew: boolean;
  originPlatform: string;
};

/** Maps a raw stub provider event onto the internal contract. */
export function normalizeStubEvent(event: StubProviderEvent): NormalizedEntitlement {
  return {
    uid: event.uid,
    eventId: event.eventId,
    status: event.status,
    lifecycle: event.lifecycle,
    memberSinceMillis: event.memberSinceMillis,
    currentPeriodEndMillis: event.currentPeriodEndMillis,
    willRenew: event.willRenew,
    // A stub stands in for a store purchase; real receipts (and their source) arrive with P7.
    source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
    originPlatform: event.originPlatform,
  };
}

/** Normalizes and applies a stub provider event through the single writer. */
export function ingestStubEntitlementEvent(event: StubProviderEvent): Promise<ApplyResult> {
  return applyEntitlement(normalizeStubEvent(event));
}
