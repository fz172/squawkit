import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  subscriptionDocPath,
} from "../src/subscription/entitlementModel.js";
import { runEntitlementReconcile } from "../src/subscription/reconcileEntitlements.js";
import { RevenueCatRateLimitError, type RevenueCatSubscriber } from "../src/subscription/revenueCatApi.js";
import {
  normalizeSubscriber,
  reconcileEventId,
} from "../src/subscription/revenueCatSubscriberSource.js";
import { PRO_ENTITLEMENT_ID } from "../src/subscription/revenueCatEntitlementSource.js";

/**
 * The reconciler (#355): the backstop that corrects `subscriptions/{uid}` when a webhook never
 * arrived. Its whole reason to exist is the incident during P7 testing where an EXPIRATION was
 * dropped as `other_entitlement` and the account would have stayed Pro forever.
 */

const NOW = 1_800_000_000_000;
const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const GRACE = HOUR;

const UID = "reconcile-user";
const OTHER = "reconcile-other";

const options = (over: Partial<Parameters<typeof runEntitlementReconcile>[0]> = {}) => ({
  apiKey: "sk_test",
  nowMillis: NOW,
  graceMs: GRACE,
  maxPerRun: 200,
  requestSpacingMs: 0,
  sleepImpl: async () => {},
  fetchSubscriberImpl: async () => null,
  ...over,
});

/** A local doc that still grants Pro, with a period end far enough past to look stale. */
function staleProDoc(over: Record<string, unknown> = {}) {
  return {
    status: SUBSCRIPTION_STATUS.PRO,
    lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
    currentPeriodEndMillis: NOW - 2 * DAY,
    memberSinceMillis: NOW - 90 * DAY,
    willRenew: true,
    originPlatform: "play_store",
    ...over,
  };
}

/** RevenueCat's view of a customer whose Pro entitlement has lapsed. */
const lapsedSubscriber: RevenueCatSubscriber = {
  entitlements: {},
  subscriptions: {
    monthly: {
      expires_date: new Date(NOW - 2 * DAY).toISOString(),
      store: "play_store",
    },
  },
};

/** RevenueCat's view of a healthy renewing customer. */
const activeSubscriber: RevenueCatSubscriber = {
  entitlements: {
    [PRO_ENTITLEMENT_ID]: {
      expires_date: new Date(NOW + 20 * DAY).toISOString(),
      product_identifier: "monthly",
      purchase_date: new Date(NOW - 90 * DAY).toISOString(),
    },
  },
  subscriptions: {
    monthly: {
      expires_date: new Date(NOW + 20 * DAY).toISOString(),
      original_purchase_date: new Date(NOW - 90 * DAY).toISOString(),
      period_type: "normal",
      store: "play_store",
    },
  },
};

const docOf = async (uid: string) => (await adminDb.doc(subscriptionDocPath(uid)).get()).data();

beforeEach(async () => {
  await adminDb.doc(subscriptionDocPath(UID)).delete();
  await adminDb.doc(subscriptionDocPath(OTHER)).delete();
  // Reconcile ids are deterministic on the resolved outcome, so a marker left by an earlier test
  // makes the next test's *first* apply look like a duplicate. Clearing them keeps each test
  // isolated — and is itself a reminder that idempotency here spans runs, not just retries.
  const markers = await adminDb
    .collection("entitlement_ingest")
    .where("uid", "in", [UID, OTHER])
    .get();
  await Promise.all(markers.docs.map((doc) => doc.ref.delete()));
});

describe("runEntitlementReconcile", () => {
  it("revokes Pro when RevenueCat says the subscription lapsed", async () => {
    // The incident this exists for: a dropped EXPIRATION left the account entitled indefinitely.
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());

    const summary = await runEntitlementReconcile(
      options({ fetchSubscriberImpl: async () => lapsedSubscriber }),
    );

    expect(summary).toMatchObject({ examined: 1, corrected: 1, failed: 0 });
    const after = await docOf(UID);
    expect(after?.status).toBe(SUBSCRIPTION_STATUS.FREE);
    expect(after?.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.EXPIRED);
  });

  it("restores the real period end when the renewal webhook was lost", async () => {
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());

    await runEntitlementReconcile(options({ fetchSubscriberImpl: async () => activeSubscriber }));

    const after = await docOf(UID);
    expect(after?.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(after?.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.ACTIVE);
    expect(after?.currentPeriodEndMillis).toBe(NOW + 20 * DAY);
  });

  it("leaves a healthy subscription alone", async () => {
    // Period end in the future — not stale, so never looked at.
    await adminDb
      .doc(subscriptionDocPath(UID))
      .set(staleProDoc({ currentPeriodEndMillis: NOW + 10 * DAY }));

    const summary = await runEntitlementReconcile(
      options({
        fetchSubscriberImpl: async () => {
          throw new Error("must not call RevenueCat for a healthy account");
        },
      }),
    );

    expect(summary.examined).toBe(0);
  });

  it("ignores an account inside the grace window, mid-renewal", async () => {
    // Between a period ending and its renewal webhook landing is normal, not drift.
    await adminDb
      .doc(subscriptionDocPath(UID))
      .set(staleProDoc({ currentPeriodEndMillis: NOW - GRACE / 2 }));

    const summary = await runEntitlementReconcile(options());

    expect(summary.examined).toBe(0);
  });

  it("ignores free accounts entirely", async () => {
    await adminDb.doc(subscriptionDocPath(UID)).set(
      staleProDoc({
        status: SUBSCRIPTION_STATUS.FREE,
        lifecycle: SUBSCRIPTION_LIFECYCLE.EXPIRED,
      }),
    );

    expect((await runEntitlementReconcile(options())).examined).toBe(0);
  });

  it("leaves a comp with no known period end alone", async () => {
    // A server grant with no end date must not send us asking RevenueCat about it every night.
    await adminDb
      .doc(subscriptionDocPath(UID))
      .set(staleProDoc({ currentPeriodEndMillis: 0, willRenew: false }));

    expect((await runEntitlementReconcile(options())).examined).toBe(0);
  });

  it("does not revoke when RevenueCat has never seen the customer", async () => {
    // A lookup that finds nothing may mean the uid was never aliased. The safe failure is a free
    // month, not locking a paying pilot out of their logbook.
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());

    const summary = await runEntitlementReconcile(
      options({ fetchSubscriberImpl: async () => null }),
    );

    expect(summary).toMatchObject({ examined: 1, corrected: 0, failed: 1 });
    expect((await docOf(UID))?.status).toBe(SUBSCRIPTION_STATUS.PRO);
  });

  it("does not revoke when the lookup errors", async () => {
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());

    const summary = await runEntitlementReconcile(
      options({
        fetchSubscriberImpl: async () => {
          throw new Error("upstream exploded");
        },
      }),
    );

    expect(summary.failed).toBe(1);
    expect((await docOf(UID))?.status).toBe(SUBSCRIPTION_STATUS.PRO);
  });

  it("stops the run on a rate limit instead of pushing through it", async () => {
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());
    await adminDb.doc(subscriptionDocPath(OTHER)).set(staleProDoc());

    let calls = 0;
    const summary = await runEntitlementReconcile(
      options({
        fetchSubscriberImpl: async () => {
          calls++;
          throw new RevenueCatRateLimitError(30);
        },
      }),
    );

    expect(calls).toBe(1);
    expect(summary.examined).toBe(1);
  });

  it("caps a run and flags the backlog", async () => {
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());
    await adminDb.doc(subscriptionDocPath(OTHER)).set(staleProDoc());

    const summary = await runEntitlementReconcile(
      options({ maxPerRun: 1, fetchSubscriberImpl: async () => lapsedSubscriber }),
    );

    expect(summary.examined).toBe(1);
    expect(summary.backlogExceeded).toBe(true);
  });

  it("is idempotent — a second run over agreeing state writes nothing", async () => {
    await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());

    const first = await runEntitlementReconcile(
      options({ fetchSubscriberImpl: async () => lapsedSubscriber }),
    );
    const second = await runEntitlementReconcile(
      options({ fetchSubscriberImpl: async () => lapsedSubscriber }),
    );

    expect(first.corrected).toBe(1);
    // The deterministic reconcile id dedups: the same resolved outcome is not rewritten.
    expect(second.corrected).toBe(0);
  });
});

describe("normalizeSubscriber", () => {
  it("reads an active renewing subscription", () => {
    const result = normalizeSubscriber(UID, activeSubscriber, NOW);
    expect(result.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.ACTIVE);
    expect(result.willRenew).toBe(true);
    expect(result.currentPeriodEndMillis).toBe(NOW + 20 * DAY);
    expect(result.memberSinceMillis).toBe(NOW - 90 * DAY);
    expect(result.originPlatform).toBe("play_store");
  });

  it("treats an unsubscribed but unexpired subscription as canceled", () => {
    const result = normalizeSubscriber(
      UID,
      withSubscription({ unsubscribe_detected_at: new Date(NOW - DAY).toISOString() }),
      NOW,
    );
    // Still PRO — access runs to the period end, exactly as the webhook's CANCELLATION does.
    expect(result.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.CANCELED);
    expect(result.willRenew).toBe(false);
  });

  it("treats a billing issue as a grace period and honours the grace end", () => {
    const graceEnd = NOW + 3 * DAY;
    const result = normalizeSubscriber(
      UID,
      withSubscription({
        billing_issues_detected_at: new Date(NOW - HOUR).toISOString(),
        grace_period_expires_date: new Date(graceEnd).toISOString(),
      }),
      NOW,
    );
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.GRACE);
    expect(result.currentPeriodEndMillis).toBe(graceEnd);
  });

  it("marks a trial as trialing", () => {
    const result = normalizeSubscriber(UID, withSubscription({ period_type: "trial" }), NOW);
    expect(result.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.TRIALING);
    expect(result.status).toBe(SUBSCRIPTION_STATUS.PRO);
  });

  it("lapses to FREE when the entitlement is absent or past its expiry", () => {
    expect(normalizeSubscriber(UID, lapsedSubscriber, NOW).status).toBe(SUBSCRIPTION_STATUS.FREE);
    expect(normalizeSubscriber(UID, { entitlements: {}, subscriptions: {} }, NOW).lifecycle).toBe(
      SUBSCRIPTION_LIFECYCLE.EXPIRED,
    );
  });

  it("treats a lifetime entitlement as active and never renewing", () => {
    const result = normalizeSubscriber(
      UID,
      {
        entitlements: { [PRO_ENTITLEMENT_ID]: { expires_date: null, product_identifier: "life" } },
        subscriptions: {},
      },
      NOW,
    );
    expect(result.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(result.willRenew).toBe(false);
  });

  it("maps the REST API's lower-cased stores through the same vocabulary as webhooks", () => {
    // REST says "app_store", webhooks say "APP_STORE" — one mapping, so they cannot drift apart.
    const result = normalizeSubscriber(UID, withSubscription({ store: "app_store" }), NOW);
    expect(result.originPlatform).toBe("app_store");
  });

  /**
   * The management URL (#363). The REST view is the ONLY source that carries it — webhooks do not —
   * so everything the web app can offer for managing a subscription depends on these.
   */
  describe("management URL", () => {
    const PLAY_URL = "https://play.google.com/store/account/subscriptions";

    it("carries the provider's management_url", () => {
      const result = normalizeSubscriber(UID, { ...activeSubscriber, management_url: PLAY_URL }, NOW);
      expect(result.managementUrl).toBe(PLAY_URL);
    });

    it("resolves to empty rather than undefined when the provider reports none", () => {
      // Empty, NOT undefined: the difference decides whether `applyEntitlement` clears a stale URL
      // or preserves it, and the REST view is authoritative enough to clear. The Test Store exposes
      // no management_url at all, which is exactly this case.
      expect(normalizeSubscriber(UID, activeSubscriber, NOW).managementUrl).toBe("");
      expect(
        normalizeSubscriber(UID, { ...activeSubscriber, management_url: null }, NOW).managementUrl,
      ).toBe("");
      expect(
        normalizeSubscriber(UID, { ...activeSubscriber, management_url: "   " }, NOW).managementUrl,
      ).toBe("");
    });

    it("rejects any scheme other than https", () => {
      // This value is persisted and then handed to a URI handler by the client — on web, straight
      // into the browser. A `javascript:` URL reaching Firestore would be stored XSS aimed at our
      // own subscribers, so the scheme is checked at the boundary it enters our storage.
      for (const hostile of [
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "http://play.google.com/store/account/subscriptions",
        "not a url at all",
      ]) {
        const result = normalizeSubscriber(UID, { ...activeSubscriber, management_url: hostile }, NOW);
        expect(result.managementUrl, hostile).toBe("");
      }
    });

    it("refuses the URL for a store that cannot have a management page", () => {
      // RevenueCat reports a Play management_url for a Test Store subscriber even though Play holds
      // no record of a simulated purchase. Kept in step with the webhook path, so a reconcile cannot
      // put the dead link back after an event cleared it.
      const testStore = normalizeSubscriber(
        UID,
        {
          ...withSubscription({ store: "test_store" }),
          management_url: PLAY_URL,
        },
        NOW,
      );
      expect(testStore.originPlatform).toBe("test_store");
      expect(testStore.managementUrl).toBe("");

      const promo = normalizeSubscriber(
        UID,
        { ...withSubscription({ store: "promotional" }), management_url: PLAY_URL },
        NOW,
      );
      expect(promo.managementUrl).toBe("");
    });

    it("still carries the URL for a lapsed subscriber", () => {
      // A subscriber whose Pro has expired may still have a store page worth reaching — reactivating
      // is done there. Dropping it on the lapsed path would take the link away at the moment it is
      // most useful.
      const result = normalizeSubscriber(UID, { ...lapsedSubscriber, management_url: PLAY_URL }, NOW);
      expect(result.status).toBe(SUBSCRIPTION_STATUS.FREE);
      expect(result.managementUrl).toBe(PLAY_URL);
    });

    it("survives a reconcile onto the entitlement doc", async () => {
      await adminDb.doc(subscriptionDocPath(UID)).set(staleProDoc());

      await runEntitlementReconcile(
        options({
          fetchSubscriberImpl: async () => ({ ...activeSubscriber, management_url: PLAY_URL }),
        }),
      );

      expect((await docOf(UID))?.managementUrl).toBe(PLAY_URL);
    });

    it("backfills a HEALTHY subscription that has no management URL yet", async () => {
      // The bug this exists for: `management_url` is REST-only, so a purchase whose webhook worked
      // is never reconciled — and a healthy subscription is never stale, so the drift scan skipped
      // it forever. Web would have waited for the plan to lapse before offering a Manage link.
      await adminDb
        .doc(subscriptionDocPath(UID))
        .set(staleProDoc({ currentPeriodEndMillis: NOW + 20 * DAY, source: ENTITLEMENT_SOURCE.STORE_PURCHASE }));

      const summary = await runEntitlementReconcile(
        options({
          fetchSubscriberImpl: async () => ({ ...activeSubscriber, management_url: PLAY_URL }),
        }),
      );

      expect(summary.examined).toBe(1);
      expect((await docOf(UID))?.managementUrl).toBe(PLAY_URL);
    });

    it("stops backfilling once the URL has been resolved, even to empty", async () => {
      // Keyed on the field being ABSENT, not falsy. The Test Store reports no management_url, so
      // those accounts settle on "" — and because the reconcile event id is deterministic, the write
      // that would clear them again is skipped as a duplicate. A falsy test would re-query them
      // every day forever with no way to age out.
      await adminDb
        .doc(subscriptionDocPath(UID))
        .set(staleProDoc({ currentPeriodEndMillis: NOW + 20 * DAY, source: ENTITLEMENT_SOURCE.STORE_PURCHASE, managementUrl: "" }));

      const summary = await runEntitlementReconcile(options());

      expect(summary.examined).toBe(0);
    });

    it("backfills an account that was ALREADY reconciled into its current state", async () => {
      // The trap the URL is part of the event id for. This account was reconciled before (a dropped
      // webhook, repaired), so a marker already exists for its current lifecycle + period end. If the
      // id ignored the URL, the backfill run would recompute that same id, hit the marker, skip the
      // write — and re-select the account every night forever, never settling.
      const periodEnd = NOW + 20 * DAY;
      await adminDb
        .doc(subscriptionDocPath(UID))
        .set(
          staleProDoc({
            currentPeriodEndMillis: periodEnd,
            source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
          }),
        );
      // The marker a previous URL-less reconcile of this exact state would have left behind.
      await adminDb
        .doc(`entitlement_ingest/${reconcileEventId(UID, SUBSCRIPTION_LIFECYCLE.ACTIVE, periodEnd)}`)
        .set({ uid: UID, appliedAtMillis: NOW - DAY });

      await runEntitlementReconcile(
        options({
          fetchSubscriberImpl: async () => ({ ...activeSubscriber, management_url: PLAY_URL }),
        }),
      );

      expect((await docOf(UID))?.managementUrl).toBe(PLAY_URL);
    });

    it("does not backfill a server grant", async () => {
      // A comp has no store behind it, so RevenueCat may not know the customer at all — the lookup
      // would trip the "entitled account RevenueCat has never seen" alarm on every run, and there is
      // nothing for a comped pilot to manage anyway.
      await adminDb
        .doc(subscriptionDocPath(UID))
        .set(staleProDoc({ currentPeriodEndMillis: NOW + 20 * DAY, source: ENTITLEMENT_SOURCE.SERVER_GRANT }));

      const summary = await runEntitlementReconcile(options());

      expect(summary.examined).toBe(0);
    });
  });
});

/** The active subscriber with its single subscription overridden. */
function withSubscription(over: Record<string, unknown>): RevenueCatSubscriber {
  return {
    entitlements: activeSubscriber.entitlements,
    subscriptions: { monthly: { ...activeSubscriber.subscriptions!.monthly, ...over } },
  };
}
