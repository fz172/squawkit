import { createHash } from "node:crypto";

import { Timestamp } from "firebase-admin/firestore";
import functionsTest from "firebase-functions-test";
import { afterAll, afterEach, beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage } from "../src/config/firebaseAdmin.js";
import { onAircraftDeleted } from "../src/sharing/onAircraftDeleted.js";
import { redeemAircraftShareInvite } from "../src/sharing/redeemAircraftShareInvite.js";
import { revokeAircraftShare as revoke } from "../src/sharing/revokeAircraftShare.js";
import { updateAircraftShareRole } from "../src/sharing/updateAircraftShareRole.js";

const fft = functionsTest();
const wrappedRedeem = fft.wrap(redeemAircraftShareInvite);
const wrappedRevoke = fft.wrap(revoke);
const wrappedUpdateRole = fft.wrap(updateAircraftShareRole);
const wrappedDeleted = fft.wrap(onAircraftDeleted);

const APP_ID = "1:811416892017:android:27fbaf1c76bb16a3f961d0";
const HOST = "host-uid";
const OWNER2 = "owner2-uid";
const TECH = "tech-uid";
const AC = "ac-1";

const sha256 = (s: string) => createHash("sha256").update(s).digest("hex");

function req(uid: string, data: unknown, provider = "google.com") {
  return {
    data,
    auth: { uid, token: { firebase: { sign_in_provider: provider } } },
    app: { appId: APP_ID },
  } as never;
}

async function seedShare(memberRoles: Record<string, string> = { [HOST]: "owner" }) {
  await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).set({ hostUid: HOST, aircraftId: AC, memberRoles });
}

async function seedInvite(overrides: Record<string, unknown> = {}) {
  const secret = "secret-xyz";
  await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/invites/${sha256(secret)}`).set({
    role: "technician",
    createdBy: HOST,
    createdAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + 3600_000),
    maxUses: 1,
    useCount: 0,
    revoked: false,
    ...overrides,
  });
  return secret;
}

async function wipe() {
  await adminDb.recursiveDelete(adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`));
  for (const u of [HOST, OWNER2, TECH]) {
    await adminDb.recursiveDelete(adminDb.doc(`users/${u}`));
    await adminStorage.bucket().deleteFiles({ prefix: `users/${u}/` });
  }
}

beforeEach(wipe);
afterEach(wipe);
afterAll(() => fft.cleanup());

// redeemAircraftShareInvite is now pairing-code based (#164) — see invite-codes.test.ts. The old
// secret-in-the-link mechanism it used to test is gone, along with the aircraft id it exposed.

describe("revokeAircraftShare", () => {
  it("an owner removes a member: ACL cleared, member doc deleted, ref tombstoned", async () => {
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/members/${TECH}`).set({ role: "technician" });
    await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).set({ deleted: false });

    await wrappedRevoke(req(HOST, { hostUid: HOST, aircraftId: AC, memberUid: TECH }));

    const share = (await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).data();
    expect(share?.memberRoles[TECH]).toBeUndefined();
    expect((await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/members/${TECH}`).get()).exists).toBe(false);
    expect((await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).get()).data()?.deleted).toBe(true);
  });

  it("leaves the host's blobs untouched — a revoke is not a delete (P8.5 #246)", async () => {
    // Removing a member must never destroy host-owned bytes. Those blobs stay reachable for the
    // host and for every remaining member; only the revoked member's local cache is dropped, which
    // the client does with a local purge that never tombstones (SharedScopeJanitor).
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/members/${TECH}`).set({ role: "technician" });
    await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).set({ deleted: false });
    const blob = `users/${HOST}/aircraft/${AC}/blobs/keep-me`;
    await adminStorage.bucket().file(blob).save(Buffer.from([1, 2, 3]));

    await wrappedRevoke(req(HOST, { hostUid: HOST, aircraftId: AC, memberUid: TECH }));

    const [exists] = await adminStorage.bucket().file(blob).exists();
    expect(exists).toBe(true);
  });

  it("cannot remove the hosting owner", async () => {
    await seedShare({ [HOST]: "owner", [OWNER2]: "owner" });
    await expect(wrappedRevoke(req(OWNER2, { hostUid: HOST, aircraftId: AC, memberUid: HOST }))).rejects.toThrow();
  });

  it("a technician cannot remove another member", async () => {
    await seedShare({ [HOST]: "owner", [TECH]: "technician", [OWNER2]: "owner" });
    await expect(wrappedRevoke(req(TECH, { hostUid: HOST, aircraftId: AC, memberUid: OWNER2 }))).rejects.toThrow();
  });
});

describe("updateAircraftShareRole", () => {
  it("an owner promotes a technician to owner and rewrites the ref", async () => {
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/members/${TECH}`).set({ role: "technician" });

    await wrappedUpdateRole(req(HOST, { hostUid: HOST, aircraftId: AC, memberUid: TECH, role: "owner" }));

    const share = (await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).data();
    expect(share?.memberRoles[TECH]).toBe("owner");
    const ref = await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).get();
    expect(ref.exists).toBe(true);
  });

  it("cannot change the hosting owner's role", async () => {
    await seedShare({ [HOST]: "owner", [OWNER2]: "owner" });
    await expect(
      wrappedUpdateRole(req(OWNER2, { hostUid: HOST, aircraftId: AC, memberUid: HOST, role: "technician" })),
    ).rejects.toThrow();
  });

  it("a technician cannot change roles", async () => {
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    await expect(
      wrappedUpdateRole(req(TECH, { hostUid: HOST, aircraftId: AC, memberUid: TECH, role: "owner" })),
    ).rejects.toThrow();
  });
});

describe("onAircraftDeleted", () => {
  const aircraftPath = `users/${HOST}/aircraft/${AC}`;

  function change(beforeDeleted: boolean, afterDeleted: boolean) {
    const before = fft.firestore.makeDocumentSnapshot({ deleted: beforeDeleted }, aircraftPath);
    const after = fft.firestore.makeDocumentSnapshot({ deleted: afterDeleted }, aircraftPath);
    return { data: fft.makeChange(before, after), params: { uid: HOST, acId: AC } };
  }

  it("tears down the share and tombstones member refs on delete", async () => {
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).set({ deleted: false });

    await wrappedDeleted(change(false, true) as never);

    expect((await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).exists).toBe(false);
    expect((await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).get()).data()?.deleted).toBe(true);
  });

  it("cascades: tombstones child records", async () => {
    await adminDb.doc(`${aircraftPath}/maintenance_log/log-1`).set({ deleted: false, note: "x" });
    await adminDb.doc(`${aircraftPath}/squawk/sq-1`).set({ deleted: false });

    await wrappedDeleted(change(false, true) as never);

    expect((await adminDb.doc(`${aircraftPath}/maintenance_log/log-1`).get()).data()?.deleted).toBe(true);
    expect((await adminDb.doc(`${aircraftPath}/squawk/sq-1`).get()).data()?.deleted).toBe(true);
  });

  it("does NOT tear down a share when a DIFFERENT user deletes an aircraft with the same id", async () => {
    // A share is keyed by aircraft id, but an aircraft id is only unique within one user's tree. A
    // member can hold a doc at users/{them}/aircraft/{acId} carrying the host's aircraft id — and
    // deleting it must not destroy the host's share. Only the host's delete ends the share (§3.3).
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).set({ deleted: false });

    const techPath = `users/${TECH}/aircraft/${AC}`;
    const before = fft.firestore.makeDocumentSnapshot({ deleted: false }, techPath);
    const after = fft.firestore.makeDocumentSnapshot({ deleted: true }, techPath);
    await wrappedDeleted({
      data: fft.makeChange(before, after),
      params: { uid: TECH, acId: AC },
    } as never);

    // The host's share is untouched: ACL, member docs, and the member's ref all survive.
    expect((await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).exists).toBe(true);
    expect((await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).get()).data()?.deleted)
      .toBe(false);
  });

  it("still cascades that user's own children when they delete their own aircraft", async () => {
    // The teardown is host-only, but the cascade is not — the deleter's own child records must
    // still be tombstoned, or they orphan.
    await seedShare({ [HOST]: "owner", [TECH]: "technician" });
    const techPath = `users/${TECH}/aircraft/${AC}`;
    await adminDb.doc(`${techPath}/maintenance_log/log-1`).set({ deleted: false });

    const before = fft.firestore.makeDocumentSnapshot({ deleted: false }, techPath);
    const after = fft.firestore.makeDocumentSnapshot({ deleted: true }, techPath);
    await wrappedDeleted({
      data: fft.makeChange(before, after),
      params: { uid: TECH, acId: AC },
    } as never);

    expect((await adminDb.doc(`${techPath}/maintenance_log/log-1`).get()).data()?.deleted).toBe(true);
    expect((await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).exists).toBe(true);
  });

  it("ignores a non-delete write (deleted stays false)", async () => {
    await adminDb.doc(`${aircraftPath}/maintenance_log/log-1`).set({ deleted: false });
    await wrappedDeleted(change(false, false) as never);
    expect((await adminDb.doc(`${aircraftPath}/maintenance_log/log-1`).get()).data()?.deleted).toBe(false);
  });
});
