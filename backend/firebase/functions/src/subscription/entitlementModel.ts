/**
 * The internal, provider-agnostic entitlement contract (subscription_design.html §7).
 *
 * Every billing source — a store notification, an aggregator webhook, or a manual server grant —
 * normalizes into a [NormalizedEntitlement] and hands it to the single writer, `applyEntitlement`.
 * That keeps the whole client + writer buildable and testable before a billing provider is chosen.
 */

/**
 * Enum values mirrored from `core/model/src/commonMain/proto/settings/subscription.proto`. The
 * `subscriptions/{uid}` doc is stored as plain fields (not an opaque proto payload), so the server
 * writes the enums as their proto int values and the client reads them back the same way
 * (`SubscriptionDocWire`). Kept in sync with the proto by hand; the client side is guarded by
 * `SubscriptionDocWireTest`.
 */
export const SUBSCRIPTION_STATUS = { FREE: 0, PRO: 1 } as const;

export const SUBSCRIPTION_LIFECYCLE = {
  NONE: 0,
  TRIALING: 1,
  ACTIVE: 2,
  CANCELED: 3,
  GRACE: 4,
  EXPIRED: 5,
} as const;

export const ENTITLEMENT_SOURCE = {
  UNSPECIFIED: 0,
  STORE_PURCHASE: 1,
  SERVER_GRANT: 2,
} as const;

/**
 * A resolved entitlement ready to persist, decoupled from any provider's event shape.
 *
 * [eventId] is the provider's transaction/event id — the idempotency key. `applyEntitlement` records
 * it so a retry or a re-delivery of the same event is a no-op rather than a second write.
 */
export type NormalizedEntitlement = {
  uid: string;
  eventId: string;
  status: number;
  lifecycle: number;
  memberSinceMillis: number;
  currentPeriodEndMillis: number;
  willRenew: boolean;
  source: number;
  originPlatform: string;
};

/** Server-authoritative entitlement doc, read by the owner and by the client's sync listener. */
export function subscriptionDocPath(uid: string): string {
  return `subscriptions/${uid}`;
}

/**
 * Idempotency marker, one per applied event. Lives OUTSIDE `subscriptions/{uid}` so the client's
 * `SubscriptionDocWire` decode never sees this internal bookkeeping, and functions-only per the
 * Firestore rules.
 */
export function entitlementIngestDocPath(eventId: string): string {
  return `entitlement_ingest/${eventId}`;
}
