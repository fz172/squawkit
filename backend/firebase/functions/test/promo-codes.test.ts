import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, fft, req } from "./helpers.js";

import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
} from "../src/subscription/entitlementModel.js";
import {
  formatPromoCode,
  generatePromoCode,
  normalizePromoCode,
  promoCodeDocPath,
  type PromoCodeDoc,
} from "../src/subscription/promoCodes.js";
import { redeemPromoCode } from "../src/subscription/redeemPromoCode.js";

const redeem = fft.wrap(redeemPromoCode);

const PILOT = "pilot-uid";
const OTHER = "other-uid";
const CODE = "PRQK8H3MXTVB";
const MS_PER_DAY = 24 * 60 * 60 * 1000;

const subDoc = (uid = PILOT) => adminDb.doc(`subscriptions/${uid}`);
const codeDoc = (code = CODE) => adminDb.doc(promoCodeDocPath(code));

async function seedCode(overrides: Partial<PromoCodeDoc> = {}, code = CODE) {
  await codeDoc(code).set({
    durationDays: 30,
    batch: "test-batch",
    createdAtMillis: Date.now(),
    expiresAtMillis: 0,
    redeemedByUid: null,
    redeemedAtMillis: null,
    grantedUntilMillis: null,
    ...overrides,
  });
}

beforeEach(async () => {
  await Promise.all([subDoc().delete(), subDoc(OTHER).delete()]);
  await adminDb.recursiveDelete(adminDb.collection("entitlement_ingest"));
  await adminDb.recursiveDelete(adminDb.collection("promo_codes"));
  await adminDb.recursiveDelete(adminDb.collection("promo_attempts"));
});

describe("promo code format", () => {
  it("round-trips a generated code through display formatting", () => {
    const code = generatePromoCode();
    expect(code).toHaveLength(12);
    expect(formatPromoCode(code)).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    expect(normalizePromoCode(formatPromoCode(code))).toBe(code);
  });

  it("accepts lowercase and stray whitespace", () => {
    expect(normalizePromoCode(" prqk 8h3m-xtvb ")).toBe(CODE);
  });

  it("rejects rather than filters a character outside the alphabet", () => {
    // The bug this exists for: dropping unknown characters turns junk into a well-formed code.
    expect(normalizePromoCode("PRQK-8H3M-XTV0")).toBe("");
    expect(normalizePromoCode("PRQK8H3MXTV")).toBe("");
  });
});

describe("redeemPromoCode", () => {
  it("grants a comp'd Pro term and marks the code used, by whom", async () => {
    await seedCode({ durationDays: 90 });
    const before = Date.now();

    const result = await redeem(req(PILOT, { code: formatPromoCode(CODE) }));

    expect(result.durationDays).toBe(90);
    expect(result.currentPeriodEndMillis).toBeGreaterThan(before + 89 * MS_PER_DAY);

    const sub = (await subDoc().get()).data();
    expect(sub?.status).toBe(SUBSCRIPTION_STATUS.PRO);
    expect(sub?.lifecycle).toBe(SUBSCRIPTION_LIFECYCLE.ACTIVE);
    expect(sub?.source).toBe(ENTITLEMENT_SOURCE.SERVER_GRANT);
    expect(sub?.originPlatform).toBe("promotional");
    // A promo does not renew; the end date is what makes it lapse on its own.
    expect(sub?.willRenew).toBe(false);
    expect(sub?.currentPeriodEndMillis).toBe(result.currentPeriodEndMillis);

    const promo = (await codeDoc().get()).data() as PromoCodeDoc;
    expect(promo.redeemedByUid).toBe(PILOT);
    expect(promo.redeemedAtMillis).toBeGreaterThanOrEqual(before);
    expect(promo.grantedUntilMillis).toBe(result.currentPeriodEndMillis);
  });

  it("refuses a code already spent by someone else, without saying it exists", async () => {
    await seedCode({ redeemedByUid: OTHER, redeemedAtMillis: Date.now() });

    await expect(redeem(req(PILOT, { code: CODE }))).rejects.toThrow(/not valid/i);
    expect((await subDoc().get()).exists).toBe(false);
    // Still spent by its original redeemer.
    expect(((await codeDoc().get()).data() as PromoCodeDoc).redeemedByUid).toBe(OTHER);
  });

  it("refuses an unknown code and spends a guessing attempt", async () => {
    await expect(redeem(req(PILOT, { code: "ABCDEFGHJKMN" }))).rejects.toThrow(/not valid/i);
    expect((await adminDb.doc(`promo_attempts/${PILOT}`).get()).data()?.failures).toBe(1);
  });

  it("refuses a code whose redemption window has closed", async () => {
    await seedCode({ expiresAtMillis: Date.now() - 1 });
    await expect(redeem(req(PILOT, { code: CODE }))).rejects.toThrow(/not valid/i);
  });

  it("replays a code this account already redeemed, granting the same end date once", async () => {
    await seedCode();
    const first = await redeem(req(PILOT, { code: CODE }));
    const second = await redeem(req(PILOT, { code: CODE }));

    // The same date, not one sliding forward by however long the retry took.
    expect(second.currentPeriodEndMillis).toBe(first.currentPeriodEndMillis);
    expect((await subDoc().get()).data()?.currentPeriodEndMillis).toBe(
      first.currentPeriodEndMillis,
    );
    // A replay costs no guessing budget.
    expect((await adminDb.doc(`promo_attempts/${PILOT}`).get()).exists).toBe(false);
  });

  it("stacks a second code onto comp time the account still holds", async () => {
    await seedCode({ durationDays: 30 });
    await seedCode({ durationDays: 30 }, "AAAABBBBCCCC");

    const first = await redeem(req(PILOT, { code: CODE }));
    const second = await redeem(req(PILOT, { code: "AAAABBBBCCCC" }));

    // Added to the existing end, not substituted for it: two codes buy two terms.
    expect(second.currentPeriodEndMillis).toBe(first.currentPeriodEndMillis + 30 * MS_PER_DAY);
  });

  it("refuses a store subscriber without spending the code", async () => {
    await subDoc().set({
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      memberSinceMillis: Date.now(),
      currentPeriodEndMillis: Date.now() + 30 * MS_PER_DAY,
      willRenew: true,
      source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
      originPlatform: "play_store",
    });
    await seedCode();

    await expect(redeem(req(PILOT, { code: CODE }))).rejects.toThrow(/already has an active/i);

    // Untouched on both sides: the paying subscription, and the code.
    expect((await subDoc().get()).data()?.source).toBe(ENTITLEMENT_SOURCE.STORE_PURCHASE);
    expect(((await codeDoc().get()).data() as PromoCodeDoc).redeemedByUid).toBeNull();
  });

  it("stacks onto a RevenueCat dashboard promo, which the webhook writes as a store purchase", async () => {
    // The comp test has to agree with the client's `isCompedEntitlement`, or the page would offer
    // an entry the server refuses. A dashboard promo is a grant wearing STORE_PURCHASE's source;
    // only its origin platform gives it away.
    const existingEnd = Date.now() + 10 * MS_PER_DAY;
    await subDoc().set({
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
      memberSinceMillis: Date.now(),
      currentPeriodEndMillis: existingEnd,
      willRenew: false,
      source: ENTITLEMENT_SOURCE.STORE_PURCHASE,
      originPlatform: "promotional",
    });
    await seedCode({ durationDays: 30 });

    const result = await redeem(req(PILOT, { code: CODE }));

    expect(result.currentPeriodEndMillis).toBe(existingEnd + 30 * MS_PER_DAY);
  });

  it("refuses a guest, whose account cannot outlive the device", async () => {
    await seedCode();
    await expect(redeem(req(PILOT, { code: CODE }, "anonymous"))).rejects.toThrow(/sign in/i);
    expect(((await codeDoc().get()).data() as PromoCodeDoc).redeemedByUid).toBeNull();
  });

  it("rejects a malformed code before it reaches the pool", async () => {
    await expect(redeem(req(PILOT, { code: "nope" }))).rejects.toThrow(/required/i);
    expect((await adminDb.doc(`promo_attempts/${PILOT}`).get()).exists).toBe(false);
  });

  it("locks out a caller who burns through the failed-attempt budget", async () => {
    for (let i = 0; i < 10; i++) {
      await expect(redeem(req(PILOT, { code: "ABCDEFGHJKMN" }))).rejects.toThrow(/not valid/i);
    }
    await seedCode();
    // Even a real code is refused while locked out — the limiter runs before the lookup.
    await expect(redeem(req(PILOT, { code: CODE }))).rejects.toThrow(/too many/i);
    expect(((await codeDoc().get()).data() as PromoCodeDoc).redeemedByUid).toBeNull();
  });
});
