import { getAuth } from "firebase-admin/auth";
import functionsTest from "firebase-functions-test";
import { afterAll, beforeEach, describe, expect, it } from "vitest";

import { deleteMyAccount } from "../src/account/deleteMyAccount.js";
import { adminDb } from "../src/config/firebaseAdmin.js";
import { sharedAircraftRefWireDoc } from "../src/sharing/sharedAircraftRefWire.js";
import { req } from "./helpers.js";

const fft = functionsTest();
const wrappedDelete = fft.wrap(deleteMyAccount);

const USER = "leaver-uid";
const MEMBER = "member-uid";
const OTHER_HOST = "other-host-uid";
const OWN_AC = "own-ac";
const JOINED_AC = "joined-ac";

async function wipe() {
  await adminDb.recursiveDelete(adminDb.collection("users"));
  await adminDb.recursiveDelete(adminDb.collection("thing_shares"));
  await adminDb.recursiveDelete(adminDb.collection("subscriptions"));
  try {
    await getAuth().deleteUser(USER);
  } catch {
    // Not every test leaves one behind.
  }
}

/**
 * Seeds an account that both hosts a share (with MEMBER in it) and belongs to someone else's,
 * because those two halves are cleaned up by completely different mechanisms and only one of them
 * is reachable from the user's own tree.
 */
async function seedAccount() {
  await getAuth().createUser({ uid: USER });

  await adminDb.doc(`users/${USER}/aircraft/${OWN_AC}`).set({ payload: "x", deleted: false });
  await adminDb.doc(`subscriptions/${USER}`).set({ tier: "heavy" });

  // A share USER hosts, with someone else in it.
  await adminDb.doc(`thing_shares/${USER}/thing/${OWN_AC}`).set({
    hostUid: USER,
    thingId: OWN_AC,
    memberRoles: { [USER]: "owner", [MEMBER]: "technician" },
  });
  await adminDb
    .doc(`users/${MEMBER}/shared_aircraft_ref/${OWN_AC}`)
    .set(sharedAircraftRefWireDoc(OWN_AC, USER, "technician"));

  // A share USER merely belongs to, hosted by someone else.
  await adminDb.doc(`thing_shares/${OTHER_HOST}/thing/${JOINED_AC}`).set({
    hostUid: OTHER_HOST,
    thingId: JOINED_AC,
    memberRoles: { [OTHER_HOST]: "owner", [USER]: "technician" },
  });
  await adminDb
    .doc(`thing_shares/${OTHER_HOST}/thing/${JOINED_AC}/members/${USER}`)
    .set({ role: "technician", displayName: "Leaver" });
  await adminDb
    .doc(`users/${USER}/shared_aircraft_ref/${JOINED_AC}`)
    .set(sharedAircraftRefWireDoc(JOINED_AC, OTHER_HOST, "technician"));

  // An N1 push token (notifications_design.md §7.1).
  await adminDb.doc(`users/${USER}/push_devices/install-1`).set({
    token: "tok-leaver",
    platform: "android",
    enabled: true,
  });
}

describe("deleteMyAccount", () => {
  beforeEach(wipe);
  afterAll(async () => {
    await wipe();
    fft.cleanup();
  });

  it("removes the user's own tree, entitlement and auth record", async () => {
    await seedAccount();

    await wrappedDelete(req(USER, undefined));

    expect((await adminDb.doc(`users/${USER}/aircraft/${OWN_AC}`).get()).exists).toBe(false);
    expect((await adminDb.doc(`subscriptions/${USER}`).get()).exists).toBe(false);
    await expect(getAuth().getUser(USER)).rejects.toThrow();
  });

  /**
   * A surviving push token keeps a deleted account's device receiving notifications about aircraft
   * it no longer has any claim on — and with the account gone there is nothing left to switch them
   * off from (notifications_design.md §12.3).
   *
   * `recursiveDelete` on `users/{uid}` already takes every subcollection, so this needs no separate
   * step in the function. It needs a test precisely *because* it needs no step: nothing in
   * `deleteMyAccount` mentions push, so nothing would notice if the delete stopped being recursive.
   */
  it("clears the account's push tokens", async () => {
    await seedAccount();

    await wrappedDelete(req(USER, undefined));

    expect((await adminDb.collection(`users/${USER}/push_devices`).get()).empty).toBe(true);
  });

  /** The data lives in the host's tree, so it cannot outlive the host (#418). */
  it("tears down a hosted share and tombstones its members", async () => {
    await seedAccount();

    await wrappedDelete(req(USER, undefined));

    expect((await adminDb.doc(`thing_shares/${USER}/thing/${OWN_AC}`).get()).exists)
      .toBe(false);
    // A tombstone, not a deletion: it is what tells the ex-member's devices to purge their copy.
    const memberRef = await adminDb.doc(`users/${MEMBER}/shared_aircraft_ref/${OWN_AC}`).get();
    expect(memberRef.exists).toBe(true);
    expect(memberRef.data()?.deleted).toBe(true);
  });

  /**
   * The membership record lives in the *host's* tree, so the recursive delete of `users/{uid}`
   * cannot reach it. Left behind, the host keeps listing a member who no longer exists and the
   * dead uid stays readable in memberRoles by everyone else on that aircraft.
   */
  it("leaves shares hosted by other people", async () => {
    await seedAccount();

    await wrappedDelete(req(USER, undefined));

    const share = await adminDb.doc(`thing_shares/${OTHER_HOST}/thing/${JOINED_AC}`).get();
    expect(share.exists).toBe(true); // someone else's share survives
    expect(share.data()?.memberRoles).not.toHaveProperty(USER);
    expect(share.data()?.memberRoles).toHaveProperty(OTHER_HOST);
    expect(
      (await adminDb
        .doc(`thing_shares/${OTHER_HOST}/thing/${JOINED_AC}/members/${USER}`)
        .get()).exists,
    ).toBe(false);
  });

  /** A guest has no cloud account; deleting one would destroy the owner of their on-device data. */
  it("refuses a guest session", async () => {
    await expect(wrappedDelete(req("guest-uid", undefined, "anonymous"))).rejects.toThrow(
      /Guest sessions/,
    );
  });

  it("refuses an unauthenticated caller", async () => {
    await expect(wrappedDelete({ data: undefined } as never)).rejects.toThrow(/Sign-in required/);
  });
});
