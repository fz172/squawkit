import { Timestamp } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage } from "./helpers.js";

import { Attachment, AttachmentType } from "../src/generated/proto/aircraft/attachment.js";
import { MaintenanceLog } from "../src/generated/proto/aircraft/maintenance_log.js";
import { runStorageSweep } from "../src/storage/storageSweep.js";

const UID = "user-sweep";
const AC = "ac-sweep";

const DEFAULTS = { dryRun: false, tombstoneRetentionDays: 30, orphanGraceDays: 7, onlyUid: UID };

const daysAgo = (n: number) => Timestamp.fromMillis(Date.now() - n * 24 * 60 * 60 * 1000);
const blobPath = (id: string) => `users/${UID}/aircraft/${AC}/blobs/${id}`;

function logPayload(...ids: string[]): Buffer {
  const attachments = ids.map((id) =>
    Attachment.fromPartial({ id, name: `${id}.jpg`, type: AttachmentType.ATTACHMENT_TYPE_IMAGE }),
  );
  return Buffer.from(MaintenanceLog.encode(MaintenanceLog.fromPartial({ id: "l", attachments })).finish());
}

async function putLog(id: string, opts: { deleted: boolean; ageDays?: number; blobs?: string[] }) {
  await adminDb.doc(`users/${UID}/aircraft/${AC}/maintenance_log/${id}`).set({
    deleted: opts.deleted,
    schema: "aircraft.MaintenanceLog",
    payload: logPayload(...(opts.blobs ?? [])),
    lastUpdateTimestamp: daysAgo(opts.ageDays ?? 0),
  });
}

async function putBlob(id: string) {
  await adminStorage.bucket().file(blobPath(id)).save(Buffer.from([1, 2, 3]));
}

async function blobExists(id: string): Promise<boolean> {
  const [exists] = await adminStorage.bucket().file(blobPath(id)).exists();
  return exists;
}

async function logExists(id: string): Promise<boolean> {
  return (await adminDb.doc(`users/${UID}/aircraft/${AC}/maintenance_log/${id}`).get()).exists;
}

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.doc(`users/${UID}`));
  await adminStorage.bucket().deleteFiles({ prefix: `users/${UID}/` });
  await adminDb.doc(`subscriptions/${UID}`).delete();
  await adminDb.doc(`users/${UID}/aircraft/${AC}`).set({ deleted: false, schema: "aircraft.Aircraft" });
});

describe("tombstone purge", () => {
  it("purges a tombstone past the retention window", async () => {
    await putLog("old", { deleted: true, ageDays: 40 });

    const report = await runStorageSweep(DEFAULTS);

    expect(report.tombstonesPurged).toBe(1);
    expect(await logExists("old")).toBe(false);
  });

  it("KEEPS a tombstone inside the window — purging early RESURRECTS the record", async () => {
    // This is the whole reason retention is a correctness constraint and not a cost knob. An offline
    // device still holding the live row will, on reconnect, find no tombstone and push the record
    // back up as a fresh write. The user believes it is deleted. It is not.
    await putLog("recent", { deleted: true, ageDays: 3 });

    const report = await runStorageSweep(DEFAULTS);

    expect(report.tombstonesPurged).toBe(0);
    expect(await logExists("recent")).toBe(true);
  });

  it("never touches a live record", async () => {
    await putLog("live", { deleted: false, ageDays: 400 });

    await runStorageSweep(DEFAULTS);

    expect(await logExists("live")).toBe(true);
  });
});

describe("orphan blob collection", () => {
  it("collects a blob no live record references — including the pre-existing backlog", async () => {
    // Nothing points at this blob. It is exactly the shape of every byte orphaned before #158
    // existed, and the reason the sweep can reach them at all is that the server can decode a
    // payload and therefore *know* what is referenced.
    await putBlob("orphan");
    await putLog("live", { deleted: false, blobs: [] });

    // graceDays 0: the blob is freshly written here, and the grace window would (correctly) refuse
    // to judge it. That refusal has its own test below.
    const report = await runStorageSweep({ ...DEFAULTS, orphanGraceDays: 0 });

    expect(report.orphanBlobsCollected).toBe(1);
    expect(await blobExists("orphan")).toBe(false);
  });

  it("keeps a blob a live record still shows", async () => {
    await putBlob("in-use");
    await putLog("live", { deleted: false, blobs: ["in-use"] });

    const report = await runStorageSweep({ ...DEFAULTS, orphanGraceDays: 0 });

    expect(report.orphanBlobsCollected).toBe(0);
    expect(await blobExists("in-use")).toBe(true);
  });

  it("keeps a blob whose only referrer is a TOMBSTONE — that record is dead", async () => {
    await putBlob("dead-ref");
    await putLog("gone", { deleted: true, ageDays: 1, blobs: ["dead-ref"] });

    const report = await runStorageSweep({ ...DEFAULTS, orphanGraceDays: 0 });

    expect(report.orphanBlobsCollected).toBe(1);
    expect(await blobExists("dead-ref")).toBe(false);
  });

  it("SKIPS the whole aircraft when a live record will not decode", async () => {
    // We cannot know what it still holds, and a blob wrongly judged unreferenced is a photo deleted
    // for good. Skipping costs bytes; guessing costs the user their picture.
    await putBlob("maybe-orphan");
    await adminDb.doc(`users/${UID}/aircraft/${AC}/maintenance_log/corrupt`).set({
      deleted: false,
      schema: "aircraft.MaintenanceLog",
      payload: Buffer.from([0xff, 0xff, 0xff, 0xff]),
    });

    const report = await runStorageSweep({ ...DEFAULTS, orphanGraceDays: 0 });

    expect(report.aircraftSkipped).toBe(1);
    expect(report.orphanBlobsCollected).toBe(0);
    expect(await blobExists("maybe-orphan")).toBe(true);
  });

  it("leaves a blob younger than the grace window alone", async () => {
    // Records and blobs travel in separate queues, so a freshly uploaded photo can land before the
    // log that references it. Without the window we would delete the picture the user just took.
    await putBlob("just-uploaded");

    const report = await runStorageSweep({ ...DEFAULTS, orphanGraceDays: 7 });

    expect(report.orphanBlobsCollected).toBe(0);
    expect(await blobExists("just-uploaded")).toBe(true);
  });
});

describe("dry run", () => {
  it("reports what it WOULD do and deletes nothing", async () => {
    await putBlob("orphan");
    await putLog("old", { deleted: true, ageDays: 40 });

    const report = await runStorageSweep({ ...DEFAULTS, dryRun: true, orphanGraceDays: 0 });

    expect(report.dryRun).toBe(true);
    expect(report.tombstonesPurged).toBe(1);
    expect(report.orphanBlobsCollected).toBe(1);
    // …and everything is still there.
    expect(await logExists("old")).toBe(true);
    expect(await blobExists("orphan")).toBe(true);
  });
});

describe("storage usage sum", () => {
  const subDoc = () => adminDb.doc(`subscriptions/${UID}`);

  it("sums blob bytes across the tree and writes storageBytesUsed", async () => {
    // grace 7 keeps these fresh blobs, so the sum sees them; each putBlob writes 3 bytes.
    await putBlob("a");
    await putBlob("b");

    const report = await runStorageSweep(DEFAULTS);

    expect(report.storageBytesByUid[UID]).toBe(6);
    expect((await subDoc().get()).data()?.storageBytesUsed).toBe(6);
  });

  it("records zero for an account with no blobs", async () => {
    const report = await runStorageSweep(DEFAULTS);

    expect(report.storageBytesByUid[UID]).toBe(0);
    expect((await subDoc().get()).data()?.storageBytesUsed).toBe(0);
  });

  it("merges usage without disturbing the billing fields on the doc", async () => {
    // The billing pipeline owns these fields; the sweep must never clobber them.
    await subDoc().set({ status: 1, memberSinceMillis: 123 });
    await putBlob("a");

    await runStorageSweep(DEFAULTS);

    const data = (await subDoc().get()).data();
    expect(data?.status).toBe(1);
    expect(data?.memberSinceMillis).toBe(123);
    expect(data?.storageBytesUsed).toBe(3);
  });

  it("counts only what remains after orphan collection", async () => {
    // The orphan is deleted earlier in the same run, so the authoritative total excludes it.
    await putBlob("orphan");
    await putLog("live", { deleted: false, blobs: [] });

    const report = await runStorageSweep({ ...DEFAULTS, orphanGraceDays: 0 });

    expect(report.orphanBlobsCollected).toBe(1);
    expect(report.storageBytesByUid[UID]).toBe(0);
    expect((await subDoc().get()).data()?.storageBytesUsed).toBe(0);
  });

  it("computes usage but writes nothing on a dry run", async () => {
    await putBlob("a");

    const report = await runStorageSweep({ ...DEFAULTS, dryRun: true });

    expect(report.storageBytesByUid[UID]).toBe(3);
    expect((await subDoc().get()).exists).toBe(false);
  });
});

describe("the report says WHAT, not just how much", () => {
  it("names the blobs and tombstones it would delete", async () => {
    // A dry run you cannot audit is not a rehearsal — it is a number you take on faith, and what is
    // on the other side of that faith is a user's photos.
    await putBlob("orphan-1");
    await putLog("old", { deleted: true, ageDays: 40 });

    const report = await runStorageSweep({ ...DEFAULTS, dryRun: true, orphanGraceDays: 0 });

    expect(report.orphanBlobPaths).toEqual([`users/${UID}/aircraft/${AC}/blobs/orphan-1`]);
    expect(report.purgedTombstonePaths).toEqual([
      `users/${UID}/aircraft/${AC}/maintenance_log/old`,
    ]);
    expect(report.truncated).toBe(false);
  });
});
