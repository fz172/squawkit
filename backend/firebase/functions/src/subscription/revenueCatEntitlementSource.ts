import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  type NormalizedEntitlement,
} from "./entitlementModel.js";

/**
 * The RevenueCat `EntitlementSource` (subscription_design.html §7) — the first *real* provider
 * ingest, replacing `stubEntitlementSource` for store purchases.
 *
 * It only normalizes. The writer it feeds (`applyEntitlement`) is unchanged, which is the whole
 * point of the abstraction: choosing RevenueCat did not reach past this file.
 */

/**
 * The entitlement identifier configured in the RevenueCat dashboard. Must match the client's
 * `PRO_ENTITLEMENT_ID` (feature/subscription/model) — they are the same dashboard string, and a
 * mismatch means purchases never grant anything.
 */
export const PRO_ENTITLEMENT_ID = "SquawkIt Pro";

/** The subset of the RevenueCat webhook event we read. Unlisted fields are ignored, not rejected. */
export type RevenueCatEvent = {
  id?: unknown;
  type?: unknown;
  app_user_id?: unknown;
  entitlement_ids?: unknown;
  store?: unknown;
  period_type?: unknown;
  environment?: unknown;
  purchased_at_ms?: unknown;
  expiration_at_ms?: unknown;
  grace_period_expiration_at_ms?: unknown;
};

/** Body shape RevenueCat POSTs: `{ api_version, event }`. */
export type RevenueCatWebhookBody = { api_version?: unknown; event?: unknown };

/**
 * Why an event produced no entitlement write. Returned rather than thrown: these are all normal,
 * and the webhook must answer 200 so RevenueCat does not retry an event we deliberately ignored.
 */
export type IgnoredReason =
  | "malformed"
  | "unhandled_type"
  | "other_entitlement"
  | "missing_app_user_id"
  | "anonymous_app_user_id";

export type NormalizeResult =
  | { kind: "entitlement"; entitlement: NormalizedEntitlement }
  | { kind: "ignored"; reason: IgnoredReason };

/**
 * RevenueCat's anonymous app-user-id prefix.
 *
 * An event carrying one of these means the purchase was never aliased to a Firebase uid — the
 * client's `BillingIdentityCoordinator` did not run `logIn(uid)` before the purchase. There is no
 * account to write an entitlement for, so it is dropped loudly: this is the failure mode where a
 * pilot has been charged and would silently stay on Free.
 */
const ANONYMOUS_ID_PREFIX = "$RCAnonymousID:";

/**
 * Maps a RevenueCat webhook event onto the internal contract.
 *
 * `status` is the tier granted and `lifecycle` the billing state; the client's `effectiveStatusAt`
 * (and its TS twin in `entitlementModel`) decides what is actually in force. That split is why a
 * cancellation still writes `status: PRO` — the pilot keeps Pro until `currentPeriodEndMillis`.
 */
export function normalizeRevenueCatEvent(event: RevenueCatEvent): NormalizeResult {
  const eventId = asString(event.id);
  const type = asString(event.type);
  const appUserId = asString(event.app_user_id);

  if (eventId == null || type == null) {
    return { kind: "ignored", reason: "malformed" };
  }

  const lifecycle = lifecycleForEventType(type, asString(event.period_type));
  if (lifecycle == null) {
    // Paywall impressions, experiment enrollments, TEST pings, virtual currency — not entitlements.
    return { kind: "ignored", reason: "unhandled_type" };
  }

  if (!grantsProEntitlement(event.entitlement_ids)) {
    return { kind: "ignored", reason: "other_entitlement" };
  }

  if (appUserId == null) {
    return { kind: "ignored", reason: "missing_app_user_id" };
  }
  if (appUserId.startsWith(ANONYMOUS_ID_PREFIX)) {
    return { kind: "ignored", reason: "anonymous_app_user_id" };
  }

  const expired = lifecycle === SUBSCRIPTION_LIFECYCLE.EXPIRED;
  const purchasedAt = asMillis(event.purchased_at_ms) ?? 0;
  // A billing-issue grace period ends at its own timestamp, not the paid-through date.
  const periodEnd =
    (lifecycle === SUBSCRIPTION_LIFECYCLE.GRACE
      ? asMillis(event.grace_period_expiration_at_ms)
      : null) ??
    asMillis(event.expiration_at_ms) ??
    0;

  return {
    kind: "entitlement",
    entitlement: {
      uid: appUserId,
      eventId,
      // On expiry the granted tier itself drops to FREE; every other lifecycle grants PRO and lets
      // the shared resolution decide whether it is currently in force.
      status: expired ? SUBSCRIPTION_STATUS.FREE : SUBSCRIPTION_STATUS.PRO,
      lifecycle,
      memberSinceMillis: purchasedAt,
      currentPeriodEndMillis: periodEnd,
      willRenew: willRenewForEventType(type),
      source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
      originPlatform: originPlatformForStore(asString(event.store)),
    },
  };
}

/**
 * Event type → billing lifecycle. `null` means "not an entitlement-bearing event".
 *
 * Only the types that change entitlement are listed; anything unrecognised is ignored rather than
 * guessed, so a future RevenueCat event type cannot silently downgrade a paying customer.
 */
function lifecycleForEventType(type: string, periodType: string | null): number | null {
  const trialing = periodType === "TRIAL";
  switch (type) {
    case "INITIAL_PURCHASE":
    case "RENEWAL":
    case "PRODUCT_CHANGE":
    case "SUBSCRIPTION_EXTENDED":
    case "REFUND_REVERSED":
      return trialing ? SUBSCRIPTION_LIFECYCLE.TRIALING : SUBSCRIPTION_LIFECYCLE.ACTIVE;

    case "UNCANCELLATION":
      return SUBSCRIPTION_LIFECYCLE.ACTIVE;

    // Bought outright — entitled, but nothing will renew.
    case "NON_RENEWING_PURCHASE":
      return SUBSCRIPTION_LIFECYCLE.ACTIVE;

    // Auto-renew off, or Play's pause scheduled: entitled until the paid-through date, then Free.
    case "CANCELLATION":
    case "SUBSCRIPTION_PAUSED":
      return SUBSCRIPTION_LIFECYCLE.CANCELED;

    // Payment failed; the store is retrying. Keep access through the grace window.
    case "BILLING_ISSUE":
      return SUBSCRIPTION_LIFECYCLE.GRACE;

    case "EXPIRATION":
      return SUBSCRIPTION_LIFECYCLE.EXPIRED;

    // Deliberately absent: TRANSFER. Moving an entitlement between app user ids needs a decision
    // about the *losing* account too, which a single normalized write cannot express. Ignored (and
    // logged by the webhook) rather than half-applied.
    default:
      return null;
  }
}

/** Whether the store will bill again. Drives the "renews on / ends on" copy. */
function willRenewForEventType(type: string): boolean {
  switch (type) {
    case "CANCELLATION":
    case "SUBSCRIPTION_PAUSED":
    case "EXPIRATION":
    case "NON_RENEWING_PURCHASE":
      return false;
    default:
      return true;
  }
}

/**
 * Which store billed the purchase — the answer to "where do I cancel this?".
 *
 * Deliberately store-specific rather than collapsed onto a device platform. An earlier version
 * mapped PLAY_STORE and AMAZON both to "android", and the three web billers all to "web", which
 * destroyed exactly the distinction this field exists to carry: cancelling an Amazon Appstore
 * subscription is a different journey from cancelling a Google Play one, and telling a subscriber
 * "Android" helps them not at all.
 *
 * The vocabulary is a closed set the client maps to display names; anything unrecognized is
 * `"unknown"`, which the UI renders as no row rather than as the word "unknown".
 */
function originPlatformForStore(store: string | null): string {
  switch (store) {
    case "APP_STORE":
      return "app_store";
    case "MAC_APP_STORE":
      return "mac_app_store";
    case "PLAY_STORE":
      return "play_store";
    case "AMAZON":
      return "amazon";
    case "STRIPE":
      return "stripe";
    case "RC_BILLING":
      return "rc_billing";
    case "PADDLE":
      return "paddle";
    // A grant made from the RevenueCat dashboard — no store, so nothing for the pilot to cancel.
    case "PROMOTIONAL":
      return "promotional";
    // The simulated store behind a Test Store key; only ever seen in a developer build.
    case "TEST_STORE":
      return "test_store";
    default:
      return "unknown";
  }
}

/** True when the event carries the Pro entitlement. */
function grantsProEntitlement(entitlementIds: unknown): boolean {
  return Array.isArray(entitlementIds) && entitlementIds.includes(PRO_ENTITLEMENT_ID);
}

function asString(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/** Coerces a millisecond timestamp, tolerating the numeric strings some providers send. */
function asMillis(value: unknown): number | null {
  const parsed = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}
