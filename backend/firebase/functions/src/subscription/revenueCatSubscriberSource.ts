import { createHash } from "node:crypto";

import { logger } from "firebase-functions/v2";

import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  type NormalizedEntitlement,
} from "./entitlementModel.js";
import {
  originPlatformForStore,
  PRO_ENTITLEMENT_ID,
} from "./revenueCatEntitlementSource.js";
import type { RevenueCatSubscriber, RevenueCatSubscription } from "./revenueCatApi.js";

/**
 * Normalizes RevenueCat's *subscriber* view (the REST pull) into the same internal contract the
 * webhook produces — the second `EntitlementSource`, per subscription_design.html §7.
 *
 * The two sources answer different questions and so read differently. A webhook says "this happened
 * just now" and carries epoch-millisecond fields; a subscriber says "this is the current state" and
 * carries ISO date strings, with the lifecycle implied by *which dates are set* rather than named by
 * an event type. Both converge on `NormalizedEntitlement`, so `applyEntitlement` stays the single
 * writer and never learns there is more than one provider path.
 */

/** A subscriber with no Pro entitlement resolves to this — the account is simply Free. */
function lapsed(
  uid: string,
  nowMillis: number,
  originPlatform: string,
  managementUrl: string,
): NormalizedEntitlement {
  return {
    uid,
    eventId: reconcileEventId(uid, SUBSCRIPTION_LIFECYCLE.EXPIRED, 0, managementUrl),
    status: SUBSCRIPTION_STATUS.FREE,
    lifecycle: SUBSCRIPTION_LIFECYCLE.EXPIRED,
    // Never known from an absent entitlement; `applyEntitlement` keeps whatever it already has.
    memberSinceMillis: 0,
    currentPeriodEndMillis: nowMillis,
    willRenew: false,
    source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
    originPlatform,
    managementUrl,
  };
}

/**
 * A deterministic idempotency key.
 *
 * `applyEntitlement` dedups on event id, and a reconcile has no provider event to borrow one from.
 * Deriving it from the *resolved outcome* means a run that finds nothing new is a no-op write rather
 * than churn: reconciling the same unchanged state tomorrow produces the same id and is skipped. A
 * genuine change produces a different id and applies.
 *
 * The management URL is part of that outcome (#363), and must be — otherwise an account already
 * reconciled into its current lifecycle and period end would recompute the *same* id when the URL is
 * later learned, hit its own marker, and skip the write. The field would stay absent, so the backfill
 * would re-select it and burn a RevenueCat lookup every night with no way to ever settle.
 *
 * Hashed rather than interpolated: this string becomes a Firestore document id under
 * `entitlement_ingest/`, and a URL contains `/`, which is illegal there.
 */
export function reconcileEventId(
  uid: string,
  lifecycle: number,
  periodEndMillis: number,
  managementUrl = "",
): string {
  const urlTag = createHash("sha1").update(managementUrl).digest("hex").slice(0, 8);
  return `reconcile:${uid}:${lifecycle}:${periodEndMillis}:${urlTag}`;
}

/**
 * Resolves RevenueCat's current view of [uid] into an entitlement.
 *
 * `null` means "RevenueCat has never heard of this customer" — distinct from "no active
 * entitlement", because the caller must not revoke Pro on the strength of a lookup that may simply
 * have hit the wrong app user id.
 */
export function normalizeSubscriber(
  uid: string,
  subscriber: RevenueCatSubscriber,
  nowMillis: number,
): NormalizedEntitlement {
  const entitlement = subscriber.entitlements?.[PRO_ENTITLEMENT_ID];
  // The subscription backing the entitlement carries the billing detail; the entitlement itself
  // only says whether and until when access is granted.
  const productId = entitlement?.product_identifier ?? null;
  const subscription: RevenueCatSubscription | undefined =
    (productId ? subscriber.subscriptions?.[productId] : undefined) ??
    // Fall back to the longest-lived subscription so an entitlement with no product link (or a
    // renamed product) still reports a plausible store rather than "unknown".
    latestSubscription(subscriber);

  const originPlatform = originPlatformForStore(normalizeStore(subscription?.store ?? null));
  // Always resolved, never left `undefined`: the REST view is the only source that can answer this,
  // so a reconcile that finds none is the authoritative "there is no link" and must clear a stale
  // one. The webhook path is what leaves it `undefined`. See NormalizedEntitlement.managementUrl.
  const managementUrl = safeManagementUrl(subscriber.management_url);

  const expiresAt = toMillis(entitlement?.expires_date);
  // A lifetime entitlement has no expiry: entitled, nothing to wait for.
  const isLifetime = entitlement != null && entitlement.expires_date == null;

  if (entitlement == null || (!isLifetime && (expiresAt == null || expiresAt <= nowMillis))) {
    return lapsed(uid, nowMillis, originPlatform, managementUrl);
  }

  const graceEndsAt = toMillis(
    entitlement.grace_period_expires_date ?? subscription?.grace_period_expires_date,
  );
  const unsubscribedAt = toMillis(subscription?.unsubscribe_detected_at);
  const billingIssueAt = toMillis(subscription?.billing_issues_detected_at);
  const periodType = (subscription?.period_type ?? "").toLowerCase();

  const lifecycle = resolveLifecycle({
    nowMillis,
    graceEndsAt,
    unsubscribedAt,
    billingIssueAt,
    periodType,
  });

  // In a grace period the entitlement runs to the grace end, not the paid-through date — same rule
  // the webhook applies to BILLING_ISSUE.
  const periodEnd =
    lifecycle === SUBSCRIPTION_LIFECYCLE.GRACE && graceEndsAt != null
      ? graceEndsAt
      : (expiresAt ?? 0);

  return {
    uid,
    eventId: reconcileEventId(uid, lifecycle, periodEnd, managementUrl),
    status: SUBSCRIPTION_STATUS.PRO,
    lifecycle,
    memberSinceMillis:
      toMillis(subscription?.original_purchase_date) ??
      toMillis(entitlement.purchase_date) ??
      0,
    currentPeriodEndMillis: periodEnd,
    // Nothing further is coming once the customer has unsubscribed; a lifetime purchase likewise
    // never renews, and must therefore not be treated as "awaiting a renewal".
    willRenew: unsubscribedAt == null && !isLifetime,
    source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
    originPlatform,
    managementUrl,
  };
}

/**
 * The provider's `management_url`, or `""` if it is absent or not something we are willing to hand
 * to a client's URI handler.
 *
 * Scheme-checked here rather than on the client because this is the boundary the value crosses from
 * a third party into our own storage: the web app opens whatever ends up in this field, so a
 * `javascript:` or `data:` URL reaching Firestore would be a stored redirect/XSS vector aimed at
 * our own users. `https:` only — every real store management page is https, and RevenueCat's own
 * `management_url` always is.
 *
 * Note the failure mode is deliberately silent-and-empty rather than an exception: a malformed URL
 * must not abort a reconcile that is otherwise correctly resolving whether someone keeps their Pro.
 */
function safeManagementUrl(url: string | null | undefined): string {
  if (typeof url !== "string") return "";
  const trimmed = url.trim();
  if (trimmed.length === 0) return "";
  try {
    if (new URL(trimmed).protocol === "https:") return trimmed;
  } catch {
    // Falls through to the rejection log below — an unparseable URL is refused like any other.
  }
  // Loud, because the two ways this line is reached are very different: a provider that changed its
  // URL format (our bug, and every subscriber silently loses their Manage link), or a genuinely
  // hostile value. Silently returning "" would make the first indistinguishable from "no URL".
  logger.warn("Refused a non-https management_url from RevenueCat", { managementUrl: trimmed });
  return "";
}

/**
 * Which billing state the dates describe, most specific first.
 *
 * A billing issue outranks a cancellation: a subscriber who has both a failed payment and auto-renew
 * off is, right now, in the grace window, and that is what decides when access ends.
 */
function resolveLifecycle(args: {
  nowMillis: number;
  graceEndsAt: number | null;
  unsubscribedAt: number | null;
  billingIssueAt: number | null;
  periodType: string;
}): number {
  const { nowMillis, graceEndsAt, unsubscribedAt, billingIssueAt, periodType } = args;
  if (graceEndsAt != null && graceEndsAt > nowMillis) return SUBSCRIPTION_LIFECYCLE.GRACE;
  if (billingIssueAt != null) return SUBSCRIPTION_LIFECYCLE.GRACE;
  if (unsubscribedAt != null) return SUBSCRIPTION_LIFECYCLE.CANCELED;
  if (periodType === "trial" || periodType === "intro") return SUBSCRIPTION_LIFECYCLE.TRIALING;
  return SUBSCRIPTION_LIFECYCLE.ACTIVE;
}

/** The subscription with the furthest expiry, as a stand-in when the entitlement names no product. */
function latestSubscription(subscriber: RevenueCatSubscriber): RevenueCatSubscription | undefined {
  const all = Object.values(subscriber.subscriptions ?? {});
  if (all.length === 0) return undefined;
  return all.reduce((best, candidate) =>
    (toMillis(candidate.expires_date) ?? 0) > (toMillis(best.expires_date) ?? 0) ? candidate : best,
  );
}

/**
 * The REST API reports stores lower-cased (`app_store`) where webhooks use upper (`APP_STORE`).
 * Upper-casing here lets both sources share one mapping, so the vocabulary cannot drift between the
 * push and pull paths.
 */
function normalizeStore(store: string | null): string | null {
  return store == null ? null : store.toUpperCase();
}

/** ISO-8601 → epoch millis. `null` for absent or unparseable, which callers treat as "not set". */
function toMillis(date: string | null | undefined): number | null {
  if (date == null) return null;
  const parsed = Date.parse(date);
  return Number.isFinite(parsed) ? parsed : null;
}
