import functionsTest from "firebase-functions-test";
import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { ENTITLEMENT_SOURCE, SUBSCRIPTION_STATUS } from "../src/subscription/entitlementModel.js";
import { grantPromoEntitlement } from "../src/subscription/grantPromoEntitlement.js";

const fft = functionsTest();
const grant = fft.wrap(grantPromoEntitlement);

const APP_ID = "1:811416892017:android:27fbaf1c76bb16a3f961d0";
const ADMIN = "admin-uid";
const TARGET = "target-uid";
const targetDoc = () => adminDb.doc(`subscriptions/${TARGET}`);

/** A callable request; `admin` toggles the custom claim the promo grant requires. */
function req(uid: string, data: unknown, admin = false) {
  return {
    data,
    auth: { uid, token: { admin, firebase: { sign_in_provider: "google.com" } } },
    app: { appId: APP_ID },
  } as never;
}

beforeEach(async () => {
  await targetDoc().delete();
  await adminDb.recursiveDelete(adminDb.collection("entitlement_ingest"));
});

describe("grantPromoEntitlement", () => {
  it("rejects a caller without the admin claim", async () => {
    await expect(grant(req("ordinary-user", { uid: TARGET, durationDays: 30 }))).rejects.toThrow(
      /admin/i,
    );
    // …and nothing was written.
    expect((await targetDoc().get()).exists).toBe(false);
  });

  it("grants a comp'd Pro entitlement from a SERVER_GRANT", async () => {
    const before = Date.now();

    const result = await grant(req(ADMIN, { uid: TARGET, durationDays: 30 }, true));

    expect(result.applied).toBe(true);
    expect(result.currentPeriodEndMillis).toBeGreaterThan(before);
    const data = (await targetDoc().get()).data();
    expect(data?.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(data?.source).toBe(ENTITLEMENT_SOURCE.SERVER_GRANT);
    // A comp does not auto-renew; it lapses when the period ends.
    expect(data?.willRenew).toBe(false);
    expect(data?.currentPeriodEndMillis).toBe(result.currentPeriodEndMillis);
  });

  it("rejects a non-positive duration", async () => {
    await expect(grant(req(ADMIN, { uid: TARGET, durationDays: 0 }, true))).rejects.toThrow(
      /duration/i,
    );
  });

  it("rejects a missing target uid", async () => {
    await expect(grant(req(ADMIN, { durationDays: 30 }, true))).rejects.toThrow(/uid/i);
  });
});
