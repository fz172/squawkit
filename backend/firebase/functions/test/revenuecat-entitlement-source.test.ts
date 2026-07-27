import { describe, expect, it } from "vitest";

import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
} from "../src/subscription/entitlementModel.js";
import {
  normalizeRevenueCatEvent,
  PRO_ENTITLEMENT_ID,
  type RevenueCatEvent,
} from "../src/subscription/revenueCatEntitlementSource.js";

const UID = "firebase-uid-1";
const PURCHASED_AT = 1_700_000_000_000;
const EXPIRES_AT = 1_731_536_000_000;

function event(overrides: Partial<RevenueCatEvent> = {}): RevenueCatEvent {
  return {
    id: "rc-evt-1",
    type: "INITIAL_PURCHASE",
    app_user_id: UID,
    entitlement_ids: [PRO_ENTITLEMENT_ID],
    store: "APP_STORE",
    period_type: "NORMAL",
    environment: "PRODUCTION",
    purchased_at_ms: PURCHASED_AT,
    expiration_at_ms: EXPIRES_AT,
    ...overrides,
  };
}

/** Narrows to the entitlement case, failing loudly instead of silently passing on an ignore. */
function normalizeToEntitlement(e: RevenueCatEvent) {
  const result = normalizeRevenueCatEvent(e);
  if (result.kind !== "entitlement") {
    throw new Error(`Expected an entitlement, got ignored: ${result.reason}`);
  }
  return result.entitlement;
}

function ignoredReason(e: RevenueCatEvent) {
  const result = normalizeRevenueCatEvent(e);
  return result.kind === "ignored" ? result.reason : null;
}

describe("normalizeRevenueCatEvent", () => {
  it("maps an initial purchase onto an active Pro entitlement", () => {
    expect(normalizeToEntitlement(event())).toEqual({
      uid: UID,
      eventId: "rc-evt-1",
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      memberSinceMillis: PURCHASED_AT,
      currentPeriodEndMillis: EXPIRES_AT,
      willRenew: true,
      source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
      originPlatform: "app_store",
    });
  });

  it("marks a free trial as TRIALING, still granting Pro", () => {
    const result = normalizeToEntitlement(event({ period_type: "TRIAL" }));
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.TRIALING);
    expect(result.status).toBe(SUBSCRIPTION_STATUS.PRO);
  });

  it("keeps Pro granted on cancellation so access lasts until the period end", () => {
    const result = normalizeToEntitlement(event({ type: "CANCELLATION" }));
    // The tier stays PRO; only `effectiveStatusAt` decides it has lapsed, and only after the end date.
    expect(result.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.CANCELED);
    expect(result.currentPeriodEndMillis).toBe(EXPIRES_AT);
    expect(result.willRenew).toBe(false);
  });

  it("drops to FREE/EXPIRED on expiration", () => {
    const result = normalizeToEntitlement(event({ type: "EXPIRATION" }));
    expect(result.status).toBe(SUBSCRIPTION_STATUS.FREE);
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.EXPIRED);
    expect(result.willRenew).toBe(false);
  });

  it("uses the grace-period end, not the paid-through date, on a billing issue", () => {
    const graceEnd = EXPIRES_AT + 86_400_000;
    const result = normalizeToEntitlement(
      event({ type: "BILLING_ISSUE", grace_period_expiration_at_ms: graceEnd }),
    );
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.GRACE);
    expect(result.currentPeriodEndMillis).toBe(graceEnd);
  });

  it("falls back to the expiration date when a billing issue carries no grace window", () => {
    const result = normalizeToEntitlement(event({ type: "BILLING_ISSUE" }));
    expect(result.currentPeriodEndMillis).toBe(EXPIRES_AT);
  });

  it("restores an active subscription on uncancellation", () => {
    const result = normalizeToEntitlement(event({ type: "UNCANCELLATION" }));
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.ACTIVE);
    expect(result.willRenew).toBe(true);
  });

  it("records which store billed the purchase, keeping stores distinct", () => {
    const platform = (store: string) => normalizeToEntitlement(event({ store })).originPlatform;
    expect(platform("APP_STORE")).toBe("app_store");
    expect(platform("MAC_APP_STORE")).toBe("mac_app_store");
    expect(platform("PLAY_STORE")).toBe("play_store");
    expect(platform("STRIPE")).toBe("stripe");
    expect(platform("RC_BILLING")).toBe("rc_billing");
    expect(platform("PROMOTIONAL")).toBe("promotional");
    expect(platform("TEST_STORE")).toBe("test_store");
    // Play and Amazon must not collapse together — they cancel in completely different places.
    expect(platform("AMAZON")).toBe("amazon");
    expect(platform("AMAZON")).not.toBe(platform("PLAY_STORE"));
  });

  it("falls back to unknown for an unrecognised store", () => {
    expect(normalizeToEntitlement(event({ store: "SOMETHING_NEW" })).originPlatform).toBe("unknown");
  });

  it("carries the RevenueCat event id through as the idempotency key", () => {
    expect(normalizeToEntitlement(event({ id: "rc-evt-99" })).eventId).toBe("rc-evt-99");
  });

  it("tolerates millisecond timestamps sent as numeric strings", () => {
    const result = normalizeToEntitlement(
      event({ purchased_at_ms: String(PURCHASED_AT), expiration_at_ms: String(EXPIRES_AT) }),
    );
    expect(result.memberSinceMillis).toBe(PURCHASED_AT);
    expect(result.currentPeriodEndMillis).toBe(EXPIRES_AT);
  });

  describe("events that must not write an entitlement", () => {
    it("ignores an anonymous app_user_id — the purchase has no account to credit", () => {
      // The pilot has been charged but never signed in to the store identity, so there is no uid.
      expect(ignoredReason(event({ app_user_id: "$RCAnonymousID:abc123" }))).toBe(
        "anonymous_app_user_id",
      );
    });

    it("ignores an event for a different entitlement", () => {
      expect(ignoredReason(event({ entitlement_ids: ["some_other_tier"] }))).toBe(
        "other_entitlement",
      );
    });

    it("ignores non-entitlement event types", () => {
      expect(ignoredReason(event({ type: "PAYWALL_IMPRESSION" }))).toBe("unhandled_type");
      expect(ignoredReason(event({ type: "TEST" }))).toBe("unhandled_type");
      // TRANSFER needs a policy for the losing account; it must not be half-applied.
      expect(ignoredReason(event({ type: "TRANSFER" }))).toBe("unhandled_type");
    });

    it("ignores an unrecognised future event type rather than guessing", () => {
      expect(ignoredReason(event({ type: "SOME_NEW_EVENT_TYPE" }))).toBe("unhandled_type");
    });

    it("ignores malformed events", () => {
      expect(ignoredReason(event({ id: undefined }))).toBe("malformed");
      expect(ignoredReason(event({ type: undefined }))).toBe("malformed");
      expect(ignoredReason(event({ app_user_id: undefined }))).toBe("missing_app_user_id");
    });
  });
});
