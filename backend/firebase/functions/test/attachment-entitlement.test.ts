import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, fft, req } from "./helpers.js";

import { createAircraftShareInvite } from "../src/sharing/createAircraftShareInvite.js";
import {
  aircraftShareDocPath,
  SHARE_ROLE,
} from "../src/sharing/sharingModels.js";
import {
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
  isEntitledToAttachments,
  subscriptionDocPath,
} from "../src/subscription/entitlementModel.js";
import { projectAttachmentEntitlement } from "../src/subscription/projectAttachmentEntitlement.js";

/**
 * P8.7 (#248): the host's attachment entitlement is projected onto the ACL root so a member reads a
 * boolean, never the host's billing (design §9.7). Two writers maintain the boolean — the trigger on
 * a subscription change, and the bootstrap stamp when a share is first created — and both resolve the
 * entitlement through the one shared helper that mirrors the client's `effectiveStatusAt`.
 */

const NOW = 1_700_000_000_000;
const DAY = 24 * 60 * 60 * 1000;

describe("isEntitledToAttachments (mirrors client effectiveStatusAt)", () => {
  const pro = (over: Record<string, unknown> = {}) => ({
    status: SUBSCRIPTION_STATUS.PRO,
    lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
    currentPeriodEndMillis: NOW + 30 * DAY,
    ...over,
  });

  it("entitles an active Pro subscription", () => {
    expect(isEntitledToAttachments(pro(), NOW)).toBe(true);
  });

  it("entitles trialing and grace, denies free/none/expired", () => {
    expect(isEntitledToAttachments(pro({ lifecycle: SUBSCRIPTION_LIFECYCLE.TRIALING }), NOW)).toBe(true);
    expect(isEntitledToAttachments(pro({ lifecycle: SUBSCRIPTION_LIFECYCLE.GRACE }), NOW)).toBe(true);
    expect(isEntitledToAttachments(pro({ status: SUBSCRIPTION_STATUS.FREE }), NOW)).toBe(false);
    expect(isEntitledToAttachments(pro({ lifecycle: SUBSCRIPTION_LIFECYCLE.EXPIRED }), NOW)).toBe(false);
    expect(isEntitledToAttachments(pro({ lifecycle: SUBSCRIPTION_LIFECYCLE.NONE }), NOW)).toBe(false);
  });

  it("keeps a canceled subscription entitled only until its period end", () => {
    const canceled = pro({ lifecycle: SUBSCRIPTION_LIFECYCLE.CANCELED, currentPeriodEndMillis: NOW + DAY });
    expect(isEntitledToAttachments(canceled, NOW)).toBe(true);
    expect(isEntitledToAttachments(canceled, NOW + 2 * DAY)).toBe(false);
  });

  it("treats a missing doc or missing fields as free", () => {
    expect(isEntitledToAttachments(undefined, NOW)).toBe(false);
    expect(isEntitledToAttachments({ storageBytesUsed: 4096 }, NOW)).toBe(false);
  });
});

const wrapped = fft.wrap(projectAttachmentEntitlement);

const HOST = "host-ent-proj";
const OTHER = "other-ent-proj";
const AC1 = "ac-1";
const AC2 = "ac-2";

/** A subscription-doc write event at `subscriptions/{uid}`, before → after. */
function subscriptionChange(
  uid: string,
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
) {
  const path = subscriptionDocPath(uid);
  return {
    data: fft.makeChange(
      fft.firestore.makeDocumentSnapshot(before ?? {}, path),
      fft.firestore.makeDocumentSnapshot(after ?? {}, path),
    ),
    params: { uid },
  };
}

const proDoc = { status: SUBSCRIPTION_STATUS.PRO, lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE };
const freeDoc = { status: SUBSCRIPTION_STATUS.FREE, lifecycle: SUBSCRIPTION_LIFECYCLE.NONE };

async function seedShare(hostUid: string, acId: string, attachmentsEnabled?: boolean) {
  await adminDb.doc(aircraftShareDocPath(hostUid, acId)).set({
    hostUid,
    aircraftId: acId,
    memberRoles: { [hostUid]: SHARE_ROLE.OWNER },
    ...(attachmentsEnabled === undefined ? {} : { attachmentsEnabled }),
  });
}

const attachmentsEnabledOf = async (hostUid: string, acId: string) =>
  (await adminDb.doc(aircraftShareDocPath(hostUid, acId)).get()).data()?.attachmentsEnabled;

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.collection(`aircraft_shares/${HOST}/aircraft`));
  await adminDb.recursiveDelete(adminDb.collection(`aircraft_shares/${OTHER}/aircraft`));
});

describe("projectAttachmentEntitlement trigger", () => {
  it("enables all of the host's shares when they become Pro", async () => {
    await seedShare(HOST, AC1);
    await seedShare(HOST, AC2, false);

    await wrapped(subscriptionChange(HOST, freeDoc, proDoc) as never);

    expect(await attachmentsEnabledOf(HOST, AC1)).toBe(true);
    expect(await attachmentsEnabledOf(HOST, AC2)).toBe(true);
  });

  it("disables the host's shares when the subscription lapses", async () => {
    await seedShare(HOST, AC1, true);

    await wrapped(
      subscriptionChange(HOST, proDoc, { status: SUBSCRIPTION_STATUS.PRO, lifecycle: SUBSCRIPTION_LIFECYCLE.EXPIRED }) as never,
    );

    expect(await attachmentsEnabledOf(HOST, AC1)).toBe(false);
  });

  it("is a no-op when the entitlement is unchanged (e.g. the storage sweep write)", async () => {
    // The sweep writes storageBytesUsed only; the entitlement fields are identical before/after, so
    // the trigger must not touch the shares — proven by leaving a deliberately-stale value in place.
    await seedShare(HOST, AC1, true);

    await wrapped(
      subscriptionChange(
        HOST,
        { ...freeDoc, storageBytesUsed: 1 },
        { ...freeDoc, storageBytesUsed: 4096 },
      ) as never,
    );

    // Untouched: still the stale `true`, because a free host with no entitlement change was skipped.
    expect(await attachmentsEnabledOf(HOST, AC1)).toBe(true);
  });

  it("never touches another host's shares", async () => {
    await seedShare(HOST, AC1);
    await seedShare(OTHER, AC1, false);

    await wrapped(subscriptionChange(HOST, freeDoc, proDoc) as never);

    expect(await attachmentsEnabledOf(HOST, AC1)).toBe(true);
    expect(await attachmentsEnabledOf(OTHER, AC1)).toBe(false);
  });
});

describe("createAircraftShareInvite stamps attachmentsEnabled at bootstrap", () => {
  const wrappedCreate = fft.wrap(createAircraftShareInvite);

  beforeEach(async () => {
    await adminDb.doc(subscriptionDocPath(HOST)).delete();
    await adminDb.doc(`users/${HOST}/aircraft/${AC1}`).set({ deleted: false, schema: "aircraft.Aircraft" });
  });

  const createReq = () =>
    req(HOST, { aircraftId: AC1, role: SHARE_ROLE.TECHNICIAN, aircraftLabel: "N123" });

  it("bootstraps a free host's share disabled", async () => {
    await wrappedCreate(createReq());
    expect(await attachmentsEnabledOf(HOST, AC1)).toBe(false);
  });

  it("bootstraps a Pro host's share enabled", async () => {
    await adminDb.doc(subscriptionDocPath(HOST)).set(proDoc);
    await wrappedCreate(createReq());
    expect(await attachmentsEnabledOf(HOST, AC1)).toBe(true);
  });
});
