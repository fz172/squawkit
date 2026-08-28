import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage } from "./helpers.js";

import { Thing } from "../src/generated/proto/thing/thing.js";
import { runThingCleanup } from "../src/migration/thingCleanup.js";
import { runThingCutover } from "../src/migration/thingCutover.js";

/**
 * The deletion pass (task B4, §7).
 *
 * The assertions that matter most here are the REFUSALS. A deletion program is judged by what it
 * declines to remove when something is off, not by whether it can remove things when everything is
 * fine — so most of this file constructs a partially-copied destination and proves the source
 * survives.
 */

const UID = "user-cleanup";
const AC = "ac-1";
const LOG = "log-1";

const legacy = `users/${UID}/aircraft/${AC}`;
const migrated = `users/${UID}/thing/${AC}`;
const legacyBlob = `users/${UID}/aircraft/${AC}/blobs/b1`;
const migratedBlob = `users/${UID}/thing/${AC}/blobs/b1`;

const b64 = (b: Uint8Array) => Buffer.from(b).toString("base64");
const CUTOVER = { dryRun: false, onlyUids: [UID] };
const LIVE = { dryRun: false, onlyUids: [UID], graceElapsed: true };

async function seedLegacy() {
  await adminDb.doc(legacy).set({
    payload: b64(Thing.encode(Thing.fromPartial({ id: AC, tailNumber: "N1" })).finish()),
    schema: "aircraft.Aircraft",
    deleted: false,
  });
  await adminDb.doc(`${legacy}/maintenance_log/${LOG}`).set({
    payload: b64(new Uint8Array([1])),
    schema: "aircraft.MaintenanceLog",
    deleted: false,
  });
  await adminStorage.bucket().file(legacyBlob).save(Buffer.from([1, 2, 3, 4]));
}

const exists = async (path: string) => (await adminDb.doc(path).get()).exists;
const blobExists = async (path: string) =>
  (await adminStorage.bucket().file(path).exists())[0];

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.doc(`users/${UID}`));
  await adminStorage.bucket().deleteFiles({ prefix: `users/${UID}/` });
});

describe("cleanup — refusals", () => {
  it("refuses to run live without the grace window asserted", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);

    await expect(
      runThingCleanup({ dryRun: false, onlyUids: [UID], graceElapsed: false }),
    ).rejects.toThrow(/grace window has not been asserted/);

    expect(await exists(legacy)).toBe(true);
  });

  it("skips an aircraft with no counterpart at /thing/", async () => {
    await seedLegacy(); // no cutover run at all

    const report = await runThingCleanup(LIVE);

    expect(report.totals.aircraftDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/no counterpart/);
    expect(await exists(legacy)).toBe(true);
    expect(await blobExists(legacyBlob)).toBe(true);
  });

  it("skips when a record is missing at the destination", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);
    await adminDb.doc(`${migrated}/maintenance_log/${LOG}`).delete();

    const report = await runThingCleanup(LIVE);

    expect(report.totals.aircraftDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/maintenance_log documents missing/);
    expect(await exists(legacy)).toBe(true);
  });

  it("compares records by id, not by count", async () => {
    // Equal counts with different ids would pass a count check and still have lost data. This is
    // the case a count-based verification gets wrong.
    await seedLegacy();
    await runThingCutover(CUTOVER);
    await adminDb.doc(`${migrated}/maintenance_log/${LOG}`).delete();
    await adminDb.doc(`${migrated}/maintenance_log/some-other-id`).set({ deleted: false });

    const report = await runThingCleanup(LIVE);

    expect(report.totals.aircraftDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/missing at destination/);
    expect(await exists(legacy)).toBe(true);
  });

  it("skips when a blob is missing at the destination", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);
    await adminStorage.bucket().file(migratedBlob).delete();

    const report = await runThingCleanup(LIVE);

    expect(report.totals.aircraftDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/blob b1 missing/);
    expect(await blobExists(legacyBlob)).toBe(true);
  });

  it("skips when a blob's bytes differ, even at the same size", async () => {
    // The case size-only comparison misses, and the reason checksums are on by default here: this
    // is the program that makes the destination the only copy.
    await seedLegacy();
    await runThingCutover(CUTOVER);
    await adminStorage.bucket().file(migratedBlob).save(Buffer.from([9, 9, 9, 9]));

    const report = await runThingCleanup(LIVE);

    expect(report.totals.aircraftDeleted).toBe(0);
    expect(report.skipped[0].reason).toMatch(/differs by checksum/);
    expect(await blobExists(legacyBlob)).toBe(true);
  });

  it("isolates one aircraft's skip from another's deletion", async () => {
    await seedLegacy();
    await adminDb.doc(`users/${UID}/aircraft/ac-2`).set({
      payload: b64(Thing.encode(Thing.fromPartial({ id: "ac-2" })).finish()),
      schema: "aircraft.Aircraft",
      deleted: false,
    });
    await runThingCutover(CUTOVER);
    await adminStorage.bucket().file(migratedBlob).delete(); // breaks ac-1 only

    const report = await runThingCleanup(LIVE);

    expect(report.totals.aircraftDeleted).toBe(1);
    expect(report.totals.aircraftSkipped).toBe(1);
    expect(await exists(legacy)).toBe(true);
    expect(await exists(`users/${UID}/aircraft/ac-2`)).toBe(false);
  });
});

describe("cleanup — the delete", () => {
  it("removes the old subtree and blobs once the copy re-verifies", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);

    const report = await runThingCleanup(LIVE);

    expect(report.failed).toEqual([]);
    expect(report.totals.aircraftDeleted).toBe(1);
    expect(report.totals.blobsDeleted).toBe(1);
    expect(report.totals.bytesReclaimed).toBe(4);
    expect(await exists(legacy)).toBe(false);
    expect(await exists(`${legacy}/maintenance_log/${LOG}`)).toBe(false);
    expect(await blobExists(legacyBlob)).toBe(false);
  });

  it("leaves the destination completely untouched", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);

    await runThingCleanup(LIVE);

    expect(await exists(migrated)).toBe(true);
    expect(await exists(`${migrated}/maintenance_log/${LOG}`)).toBe(true);
    expect(await blobExists(migratedBlob)).toBe(true);
  });

  it("is a no-op on a second run", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);

    await runThingCleanup(LIVE);
    const second = await runThingCleanup(LIVE);

    expect(second.failed).toEqual([]);
    expect(second.totals.aircraftDeleted).toBe(0);
    expect(second.totals.aircraftSkipped).toBe(0);
  });
});

describe("cleanup — dry run", () => {
  it("counts what would go and deletes nothing", async () => {
    await seedLegacy();
    await runThingCutover(CUTOVER);

    const report = await runThingCleanup({ dryRun: true, onlyUids: [UID], graceElapsed: false });

    expect(report.totals.aircraftDeleted).toBe(1);
    expect(report.totals.blobsDeleted).toBe(1);
    expect(report.totals.bytesReclaimed).toBe(4);
    expect(await exists(legacy)).toBe(true);
    expect(await blobExists(legacyBlob)).toBe(true);
  });

  it("does not require the grace assertion, since it cannot delete", async () => {
    await seedLegacy();

    const report = await runThingCleanup({ dryRun: true, onlyUids: [UID], graceElapsed: false });

    expect(report.failed).toEqual([]);
  });
});
