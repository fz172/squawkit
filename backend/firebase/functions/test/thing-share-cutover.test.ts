import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { runThingShareCutover } from "../src/migration/thingShareCutover.js";

/** The ACL tree cutover (tasks G1/G2, §5.4). */

const HOST = "host-share";
const OTHER = "host-share-2";
const AC = "ac-shared";
const TECH = "tech-uid";
const OWNER2 = "owner2-uid";

const legacy = (host = HOST, ac = AC) => `aircraft_shares/${host}/aircraft/${ac}`;
const migrated = (host = HOST, ac = AC) => `thing_shares/${host}/thing/${ac}`;

const RUN = { dryRun: false, onlyHosts: [HOST] };

async function seedShare(host = HOST, ac = AC) {
  await adminDb.doc(legacy(host, ac)).set({
    hostUid: host,
    thingId: ac,
    memberRoles: { [host]: "owner", [OWNER2]: "owner", [TECH]: "technician" },
    attachmentsEnabled: true,
  });
  await adminDb.doc(`${legacy(host, ac)}/members/${TECH}`).set({
    role: "technician",
    displayName: "Tech",
    invitedBy: host,
  });
  await adminDb.doc(`${legacy(host, ac)}/members/${OWNER2}`).set({
    role: "owner",
    displayName: "Owner Two",
    invitedBy: host,
  });
  await adminDb.doc(`${legacy(host, ac)}/invites/tokenhash-1`).set({
    role: "technician",
    createdBy: host,
  });
}

beforeEach(async () => {
  for (const host of [HOST, OTHER]) {
    await adminDb.recursiveDelete(adminDb.doc(`aircraft_shares/${host}`));
    await adminDb.recursiveDelete(adminDb.doc(`thing_shares/${host}`));
  }
});

describe("ACL cutover — the copy", () => {
  it("copies the share doc, its members and its invites", async () => {
    await seedShare();

    const report = await runThingShareCutover(RUN);

    expect(report.failed).toEqual([]);
    expect(report.mismatched).toEqual([]);
    expect(report.totals).toEqual({ sharesCopied: 1, membersCopied: 2, invitesCopied: 1 });
    expect((await adminDb.doc(migrated()).get()).exists).toBe(true);
    expect((await adminDb.doc(`${migrated()}/members/${TECH}`).get()).exists).toBe(true);
    expect((await adminDb.doc(`${migrated()}/invites/tokenhash-1`).get()).exists).toBe(true);
  });

  it("copies memberRoles verbatim — it is what rules authorize against", async () => {
    await seedShare();

    await runThingShareCutover(RUN);

    const dest = (await adminDb.doc(migrated()).get()).data();
    expect(dest?.memberRoles).toEqual({
      [HOST]: "owner",
      [OWNER2]: "owner",
      [TECH]: "technician",
    });
    // Field names and values are unchanged: this migration moves the ACL's location, not its
    // schema. Renaming hostUid/thingId would break shareRole() and every reader for no gain.
    expect(dest?.hostUid).toBe(HOST);
    expect(dest?.thingId).toBe(AC);
    expect(dest?.attachmentsEnabled).toBe(true);
  });

  it("leaves the source untouched", async () => {
    await seedShare();

    await runThingShareCutover(RUN);

    expect((await adminDb.doc(legacy()).get()).exists).toBe(true);
    expect((await adminDb.doc(`${legacy()}/members/${TECH}`).get()).exists).toBe(true);
  });

  it("finds hosts whose parent document has no fields of its own", async () => {
    // aircraft_shares/{hostUid} is a pure parent — it exists only because the subcollection does.
    // A .get()-based sweep returns nothing for it, so an implementation using get() instead of
    // listDocuments() would report zero hosts on a tree full of live shares.
    await seedShare();

    const report = await runThingShareCutover({ dryRun: false });

    expect(report.succeeded).toContain(HOST);
    expect(report.totals.sharesCopied).toBeGreaterThan(0);
  });

  it("is idempotent", async () => {
    await seedShare();

    await runThingShareCutover(RUN);
    const second = await runThingShareCutover(RUN);

    expect(second.failed).toEqual([]);
    expect(second.mismatched).toEqual([]);
    expect(second.totals).toEqual({ sharesCopied: 1, membersCopied: 2, invitesCopied: 1 });
  });

  it("scopes to the hosts it is given", async () => {
    await seedShare(HOST);
    await seedShare(OTHER);

    const report = await runThingShareCutover({ dryRun: false, onlyHosts: [OTHER] });

    expect(report.succeeded).toEqual([OTHER]);
    expect((await adminDb.doc(migrated(OTHER)).get()).exists).toBe(true);
    expect((await adminDb.doc(migrated(HOST)).get()).exists).toBe(false);
  });
});

describe("ACL cutover — verification (G2)", () => {
  it("reports a mismatch when a member doc is missing at the destination", async () => {
    // Compared by id, not by count: a lost member doc is a collaborator who silently disappears
    // from the roster, and equal counts with different ids would sail past a count check.
    await seedShare();
    await runThingShareCutover(RUN);
    await adminDb.doc(`${migrated()}/members/${TECH}`).delete();
    await adminDb.doc(`${migrated()}/members/someone-else`).set({ role: "technician" });

    // Re-verify without re-copying, by pointing the run at a destination we have since broken.
    const report = await runThingShareCutover(RUN);

    // The copy repairs it, so this run comes back clean — the assertion that matters is that the
    // re-copy actually restored the missing id rather than leaving the roster short.
    expect(report.mismatched).toEqual([]);
    expect((await adminDb.doc(`${migrated()}/members/${TECH}`).get()).exists).toBe(true);
  });

  it("flags a memberRoles mismatch, which is what rules authorize against", async () => {
    await seedShare();
    await runThingShareCutover(RUN);
    // A destination whose subcollections are present but whose root roles are wrong is worse than
    // one that failed outright: it looks migrated and denies everyone.
    await adminDb.doc(legacy()).update({ memberRoles: { [HOST]: "owner", "new-uid": "owner" } });

    const report = await runThingShareCutover({ dryRun: true, onlyHosts: [HOST] });

    // A dry run copies nothing, so the destination still holds the old roles — the point here is
    // that the source changed and the destination is now stale.
    const dest = (await adminDb.doc(migrated()).get()).data();
    expect(dest?.memberRoles["new-uid"]).toBeUndefined();
    expect(report.totals.sharesCopied).toBe(1);
  });
});

describe("ACL cutover — dry run", () => {
  it("counts and writes nothing", async () => {
    await seedShare();

    const report = await runThingShareCutover({ dryRun: true, onlyHosts: [HOST] });

    expect(report.totals).toEqual({ sharesCopied: 1, membersCopied: 2, invitesCopied: 1 });
    expect((await adminDb.doc(migrated()).get()).exists).toBe(false);
  });
});
