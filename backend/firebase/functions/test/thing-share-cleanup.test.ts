import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { runThingShareCleanup } from "../src/migration/thingShareCleanup.js";
import { runThingShareCutover } from "../src/migration/thingShareCutover.js";

/**
 * The ACL deletion pass (task G6, §5.4 step 4).
 *
 * Weighted toward the refusals, like the entity cleanup: a deletion program is judged by what it
 * declines to remove when something is off. A lost ACL is worse than a lost record — it is every
 * member of that aircraft losing access at once.
 */

const HOST = "host-g6";
const OTHER = "host-g6-2";
const AC = "ac-g6";
const TECH = "tech-uid";

const legacy = (host = HOST, ac = AC) => `aircraft_shares/${host}/aircraft/${ac}`;
const migrated = (host = HOST, ac = AC) => `thing_shares/${host}/thing/${ac}`;

const COPY = { dryRun: false, onlyHosts: [HOST] };
const LIVE = { dryRun: false, onlyHosts: [HOST], graceElapsed: true };

const exists = async (p: string) => (await adminDb.doc(p).get()).exists;

async function seedShare(host = HOST, ac = AC) {
  await adminDb.doc(legacy(host, ac)).set({
    hostUid: host,
    thingId: ac,
    memberRoles: { [host]: "owner", [TECH]: "technician" },
  });
  await adminDb.doc(`${legacy(host, ac)}/members/${TECH}`).set({ role: "technician" });
  await adminDb.doc(`${legacy(host, ac)}/invites/hash-1`).set({ role: "technician" });
}

beforeEach(async () => {
  for (const host of [HOST, OTHER]) {
    await adminDb.recursiveDelete(adminDb.doc(`aircraft_shares/${host}`));
    await adminDb.recursiveDelete(adminDb.doc(`thing_shares/${host}`));
  }
});

describe("ACL cleanup — refusals", () => {
  it("refuses to run live without the grace window asserted", async () => {
    await seedShare();
    await runThingShareCutover(COPY);

    await expect(
      runThingShareCleanup({ dryRun: false, onlyHosts: [HOST], graceElapsed: false }),
    ).rejects.toThrow(/grace window has not been asserted/);

    expect(await exists(legacy())).toBe(true);
  });

  it("skips a share with no replica at thing_shares", async () => {
    await seedShare(); // never copied

    const report = await runThingShareCleanup(LIVE);

    expect(report.totals.sharesDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/no replica/);
    expect(await exists(legacy())).toBe(true);
  });

  it("skips when the replica is missing a memberRoles entry", async () => {
    // The field security rules actually authorize against. A replica missing an entry is a member
    // who loses access the instant the source goes.
    await seedShare();
    await runThingShareCutover(COPY);
    await adminDb.doc(migrated()).update({ memberRoles: { [HOST]: "owner" } });

    const report = await runThingShareCleanup(LIVE);

    expect(report.totals.sharesDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/memberRoles mismatch for tech-uid/);
    expect(await exists(legacy())).toBe(true);
  });

  it("skips when the replica has a role at the wrong value", async () => {
    await seedShare();
    await runThingShareCutover(COPY);
    await adminDb
      .doc(migrated())
      .update({ memberRoles: { [HOST]: "owner", [TECH]: "owner" } });

    const report = await runThingShareCleanup(LIVE);

    expect(report.skipped[0].reason).toMatch(/technician vs owner/);
    expect(await exists(legacy())).toBe(true);
  });

  it("compares member documents by id, not by count", async () => {
    await seedShare();
    await runThingShareCutover(COPY);
    await adminDb.doc(`${migrated()}/members/${TECH}`).delete();
    await adminDb.doc(`${migrated()}/members/someone-else`).set({ role: "technician" });

    const report = await runThingShareCleanup(LIVE);

    expect(report.totals.sharesDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/members missing at destination/);
    expect(await exists(legacy())).toBe(true);
  });

  it("skips when an invite is missing at the replica", async () => {
    await seedShare();
    await runThingShareCutover(COPY);
    await adminDb.doc(`${migrated()}/invites/hash-1`).delete();

    const report = await runThingShareCleanup(LIVE);

    expect(report.skipped[0].reason).toMatch(/invites missing/);
    expect(await exists(legacy())).toBe(true);
  });

  it("isolates one share's skip from another's deletion", async () => {
    await seedShare(HOST, AC);
    await seedShare(HOST, "ac-2");
    await runThingShareCutover(COPY);
    await adminDb.doc(migrated(HOST, AC)).delete(); // breaks only the first

    const report = await runThingShareCleanup(LIVE);

    expect(report.totals.sharesDeleted).toBe(1);
    expect(report.totals.sharesSkipped).toBe(1);
    expect(await exists(legacy(HOST, AC))).toBe(true);
    expect(await exists(legacy(HOST, "ac-2"))).toBe(false);
  });
});

describe("ACL cleanup — the delete", () => {
  it("removes the share and its subcollections once the replica re-verifies", async () => {
    await seedShare();
    await runThingShareCutover(COPY);

    const report = await runThingShareCleanup(LIVE);

    expect(report.failed).toEqual([]);
    expect(report.totals.sharesDeleted).toBe(1);
    expect(report.totals.documentsDeleted).toBe(3); // root + 1 member + 1 invite
    expect(await exists(legacy())).toBe(false);
    expect(await exists(`${legacy()}/members/${TECH}`)).toBe(false);
    expect(await exists(`${legacy()}/invites/hash-1`)).toBe(false);
  });

  it("leaves the replica completely untouched", async () => {
    await seedShare();
    await runThingShareCutover(COPY);

    await runThingShareCleanup(LIVE);

    expect(await exists(migrated())).toBe(true);
    expect(await exists(`${migrated()}/members/${TECH}`)).toBe(true);
    expect(await exists(`${migrated()}/invites/hash-1`)).toBe(true);
  });

  it("is a no-op on a second run", async () => {
    await seedShare();
    await runThingShareCutover(COPY);

    await runThingShareCleanup(LIVE);
    const second = await runThingShareCleanup(LIVE);

    expect(second.failed).toEqual([]);
    expect(second.totals.sharesDeleted).toBe(0);
    expect(second.totals.sharesSkipped).toBe(0);
  });
});

describe("ACL cleanup — dry run", () => {
  it("counts what would go and deletes nothing", async () => {
    await seedShare();
    await runThingShareCutover(COPY);

    const report = await runThingShareCleanup({
      dryRun: true,
      onlyHosts: [HOST],
      graceElapsed: false,
    });

    expect(report.totals.sharesDeleted).toBe(1);
    expect(report.totals.documentsDeleted).toBe(3);
    expect(await exists(legacy())).toBe(true);
  });
});
