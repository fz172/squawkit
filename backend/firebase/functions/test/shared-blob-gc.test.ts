import { Timestamp } from "firebase-admin/firestore";
import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage, fft } from "./helpers.js";

import { Attachment, AttachmentType } from "../src/generated/proto/aircraft/attachment.js";
import { MaintenanceLog } from "../src/generated/proto/aircraft/maintenance_log.js";
import { onRecordDeleted } from "../src/storage/onRecordDeleted.js";
import { runStorageSweep } from "../src/storage/storageSweep.js";

/**
 * P8.6 (#247): deletion + blob GC across accounts.
 *
 * A shared aircraft's records AND blobs both live in the HOST's tree — a member edits in place
 * (design §6.3), so a member's delete lands at `users/{host}/…`, not in their own tree. These prove
 * the canonical bytes are reclaimed no matter WHO initiated the delete, rather than being silently
 * dropped because the writer was a member operating on a foreign tree.
 *
 * Both reclaim paths are covered, because a member has two distinct ways to orphan bytes:
 *  - deleting the whole record  → the `onRecordDeleted` trigger collects immediately;
 *  - removing one attachment    → the record stays LIVE, so no trigger fires and the scheduled
 *                                 sweep is the backstop.
 */

const wrappedRecord = fft.wrap(onRecordDeleted);

const HOST = "host-shared-gc";
const MEMBER = "member-shared-gc";
const AC = "ac-shared-gc";
const LOG = "log-shared-1";

const DEFAULTS = {
  dryRun: false,
  tombstoneRetentionDays: 30,
  orphanGraceDays: 0,
  onlyUid: HOST,
};

/** Canonical blob location — the HOST's tree, whoever uploaded it. */
const blobPath = (id: string) => `users/${HOST}/aircraft/${AC}/blobs/${id}`;
const logPath = (id = LOG) => `users/${HOST}/aircraft/${AC}/maintenance_log/${id}`;

function logPayload(...ids: string[]): Buffer {
  const attachments = ids.map((id) =>
    Attachment.fromPartial({ id, name: `${id}.jpg`, type: AttachmentType.ATTACHMENT_TYPE_IMAGE }),
  );
  return Buffer.from(
    MaintenanceLog.encode(MaintenanceLog.fromPartial({ id: LOG, attachments })).finish(),
  );
}

async function putBlob(id: string) {
  await adminStorage.bucket().file(blobPath(id)).save(Buffer.from([1, 2, 3]));
}

async function blobExists(id: string): Promise<boolean> {
  const [exists] = await adminStorage.bucket().file(blobPath(id)).exists();
  return exists;
}

/**
 * A record in the host's tree written BY THE MEMBER — `writerUid` is the member, the path is the
 * host's. That split is the whole point of these tests.
 */
async function putMemberWrittenLog(
  id: string,
  opts: { deleted: boolean; blobs: string[]; ageDays?: number },
) {
  await adminDb.doc(logPath(id)).set({
    deleted: opts.deleted,
    schema: "aircraft.MaintenanceLog",
    payload: logPayload(...opts.blobs),
    writerUid: MEMBER,
    lastUpdateTimestamp: Timestamp.fromMillis(
      Date.now() - (opts.ageDays ?? 0) * 24 * 60 * 60 * 1000,
    ),
  });
}

/** The `deleted: false → true` edge as the member's client pushes it into the host's tree. */
function memberDeletion(payload: Buffer, docId = LOG) {
  const base = { schema: "aircraft.MaintenanceLog", payload, writerUid: MEMBER };
  return {
    data: fft.makeChange(
      fft.firestore.makeDocumentSnapshot({ ...base, deleted: false }, logPath(docId)),
      fft.firestore.makeDocumentSnapshot({ ...base, deleted: true }, logPath(docId)),
    ),
    // uid is the HOST — the trigger keys off the document path, not the writer.
    params: { uid: HOST, acId: AC, kind: "maintenance_log", docId },
  };
}

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.doc(`users/${HOST}`));
  await adminStorage.bucket().deleteFiles({ prefix: `users/${HOST}/` });
  await adminDb
    .doc(`users/${HOST}/aircraft/${AC}`)
    .set({ deleted: false, schema: "aircraft.Aircraft" });
});

describe("a MEMBER's delete reclaims the host's canonical bytes", () => {
  it("collects the blob when a member deletes the whole record", async () => {
    // The delete was initiated by the member, but the tombstone lands in the HOST's tree — so the
    // trigger fires with uid = host and removes the canonical blob. Nothing is skipped for being
    // "someone else's tree": the document path is the only thing that decides.
    await putBlob("member-deleted");
    const payload = logPayload("member-deleted");
    await putMemberWrittenLog(LOG, { deleted: true, blobs: ["member-deleted"] });

    await wrappedRecord(memberDeletion(payload) as never);

    expect(await blobExists("member-deleted")).toBe(false);
  });

  it("still spares a blob a co-member's LIVE record shows", async () => {
    // Two members can hold the same attachment id. A member deleting their record must not take
    // out a photo another member's live record still displays.
    await putBlob("shared-by-two");
    const payload = logPayload("shared-by-two");
    await putMemberWrittenLog(LOG, { deleted: true, blobs: ["shared-by-two"] });
    await putMemberWrittenLog("log-other", { deleted: false, blobs: ["shared-by-two"] });

    await wrappedRecord(memberDeletion(payload) as never);

    expect(await blobExists("shared-by-two")).toBe(true);
  });
});

describe("the sweep is the backstop when a member removes just one attachment", () => {
  it("reclaims a blob the member dropped from a still-LIVE record", async () => {
    // Removing one attachment UPDATES the record rather than deleting it, so onRecordDeleted never
    // fires. The member also cannot delete the object directly (storage.rules deny the host's tree,
    // and the broker has no delete door), so the sweep is what actually reclaims the bytes.
    await putBlob("dropped-by-member");
    await putMemberWrittenLog(LOG, { deleted: false, blobs: [] }); // attachment removed

    const report = await runStorageSweep(DEFAULTS);

    expect(report.orphanBlobsCollected).toBe(1);
    expect(report.orphanBlobPaths).toEqual([blobPath("dropped-by-member")]);
    expect(await blobExists("dropped-by-member")).toBe(false);
  });

  it("leaves the blobs the record still references", async () => {
    await putBlob("kept");
    await putBlob("dropped");
    await putMemberWrittenLog(LOG, { deleted: false, blobs: ["kept"] });

    const report = await runStorageSweep(DEFAULTS);

    expect(report.orphanBlobsCollected).toBe(1);
    expect(await blobExists("kept")).toBe(true);
    expect(await blobExists("dropped")).toBe(false);
  });
});
