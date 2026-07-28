import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { applyEntitlement } from "../src/subscription/applyEntitlement.js";
import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  type NormalizedEntitlement,
} from "../src/subscription/entitlementModel.js";
import { ingestStubEntitlementEvent } from "../src/subscription/stubEntitlementSource.js";

const UID = "user-ent";
const subDoc = () => adminDb.doc(`subscriptions/${UID}`);

function entitlement(overrides: Partial<NormalizedEntitlement> = {}): NormalizedEntitlement {
  return {
    uid: UID,
    eventId: "evt-1",
    status: SUBSCRIPTION_STATUS.PRO,
    lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
    memberSinceMillis: 1_700_000_000_000,
    currentPeriodEndMillis: 1_702_000_000_000,
    willRenew: true,
    source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
    originPlatform: "ios",
    ...overrides,
  };
}

beforeEach(async () => {
  await subDoc().delete();
  await adminDb.recursiveDelete(adminDb.collection("entitlement_ingest"));
});

describe("applyEntitlement", () => {
  it("writes the entitlement fields", async () => {
    const result = await applyEntitlement(entitlement());

    expect(result.applied).toBe(true);
    expect((await subDoc().get()).data()).toMatchObject({
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      memberSinceMillis: 1_700_000_000_000,
      currentPeriodEndMillis: 1_702_000_000_000,
      willRenew: true,
      source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
      originPlatform: "ios",
    });
  });

  it("persists a management URL the source knows", async () => {
    await applyEntitlement(entitlement({ managementUrl: "https://play.google.com/store/account/subscriptions" }));

    expect((await subDoc().get()).data()?.managementUrl).toBe(
      "https://play.google.com/store/account/subscriptions",
    );
  });

  it("leaves a stored management URL alone when the source cannot know it (#363)", async () => {
    // The whole reason `managementUrl` is optional rather than defaulted to "". A reconcile learns
    // the URL from the REST view; every webhook that follows — a renewal, a cancellation — carries
    // no `management_url` at all. Writing "" for those would blank the link on the next renewal and
    // web would silently fall back to "go find the device you bought it on".
    await applyEntitlement(
      entitlement({ eventId: "reconcile", managementUrl: "https://apps.apple.com/account/subscriptions" }),
    );

    await applyEntitlement(entitlement({ eventId: "renewal-webhook", managementUrl: undefined }));

    expect((await subDoc().get()).data()?.managementUrl).toBe(
      "https://apps.apple.com/account/subscriptions",
    );
  });

  it("clears a stale management URL when the provider reports none", async () => {
    // The other half: "" is a real answer, not an absent one. A subscription that moved stores (or
    // a Test Store purchase, which exposes no page at all) must stop offering a link that 404s.
    await applyEntitlement(
      entitlement({ eventId: "reconcile-1", managementUrl: "https://play.google.com/store/account/subscriptions" }),
    );

    await applyEntitlement(entitlement({ eventId: "reconcile-2", managementUrl: "" }));

    expect((await subDoc().get()).data()?.managementUrl).toBe("");
  });

  it("is idempotent — a re-delivered event does not apply twice", async () => {
    await applyEntitlement(entitlement());
    // Same event id, different payload: a retry/re-delivery must be a no-op, not an overwrite.
    const result = await applyEntitlement(
      entitlement({ status: SUBSCRIPTION_STATUS.FREE, willRenew: false }),
    );

    expect(result).toEqual({ applied: false, reason: "duplicate" });
    const data = (await subDoc().get()).data();
    expect(data?.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(data?.willRenew).toBe(true);
  });

  it("applies a distinct event id", async () => {
    await applyEntitlement(entitlement({ eventId: "evt-1" }));
    const result = await applyEntitlement(
      entitlement({ eventId: "evt-2", status: SUBSCRIPTION_STATUS.FREE }),
    );

    expect(result.applied).toBe(true);
    expect((await subDoc().get()).data()?.status).toBe(SUBSCRIPTION_STATUS.FREE);
  });

  it("merges without disturbing the sweep's storageBytesUsed", async () => {
    // The storage sweep owns storageBytesUsed; the entitlement writer must never clobber it.
    await subDoc().set({ storageBytesUsed: 4_096 });

    await applyEntitlement(entitlement());

    const data = (await subDoc().get()).data();
    expect(data?.storageBytesUsed).toBe(4_096);
    expect(data?.status).toBe(SUBSCRIPTION_STATUS.PRO);
  });
});

describe("applyEntitlement member-since", () => {
  beforeEach(async () => {
    await subDoc().delete();
  });

  it("keeps the earliest member-since when a later event reports a newer date", async () => {
    const firstPurchase = 1_600_000_000_000;
    const renewal = 1_700_000_000_000;

    await applyEntitlement(entitlement({ eventId: "e1", memberSinceMillis: firstPurchase }));
    await applyEntitlement(entitlement({ eventId: "e2", memberSinceMillis: renewal }));

    // A renewal reports the renewal date; "member since" must not walk forward with it.
    expect((await subDoc().get()).data()?.memberSinceMillis).toBe(firstPurchase);
  });

  it("does not let an unknown (zero) member-since clobber a stored purchase date", async () => {
    const firstPurchase = 1_600_000_000_000;

    await applyEntitlement(entitlement({ eventId: "e1", memberSinceMillis: firstPurchase }));
    await applyEntitlement(entitlement({ eventId: "e2", memberSinceMillis: 0 }));

    expect((await subDoc().get()).data()?.memberSinceMillis).toBe(firstPurchase);
  });

  it("accepts the incoming date when nothing is stored yet", async () => {
    await applyEntitlement(entitlement({ eventId: "e1", memberSinceMillis: 1_650_000_000_000 }));

    expect((await subDoc().get()).data()?.memberSinceMillis).toBe(1_650_000_000_000);
  });
});

describe("stub entitlement ingest", () => {
  it("normalizes a raw provider event and applies it through the writer", async () => {
    const result = await ingestStubEntitlementEvent({
      eventId: "stub-1",
      uid: UID,
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.TRIALING,
      memberSinceMillis: 1,
      currentPeriodEndMillis: 2,
      willRenew: true,
      originPlatform: "android",
    });

    expect(result.applied).toBe(true);
    const data = (await subDoc().get()).data();
    expect(data?.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.TRIALING);
    // The stub stands in for a store purchase until a real provider lands.
    expect(data?.source).toBe(ENTITLEMENT_SOURCE.STORE_PURCHASE);
    expect(data?.originPlatform).toBe("android");
  });
});
