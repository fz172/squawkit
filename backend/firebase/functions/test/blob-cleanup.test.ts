import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage, fft } from "./helpers.js";

import { Attachment, AttachmentType } from "../src/generated/proto/aircraft/attachment.js";
import { MaintenanceLog } from "../src/generated/proto/aircraft/maintenance_log.js";
import { Squawk } from "../src/generated/proto/aircraft/squawk.js";
import { onAircraftDeleted } from "../src/sharing/onAircraftDeleted.js";
import { onRecordDeleted } from "../src/storage/onRecordDeleted.js";

const wrappedRecord = fft.wrap(onRecordDeleted);
const wrappedAircraft = fft.wrap(onAircraftDeleted);

const UID = "user-gc";
const AC = "ac-gc";
const LOG = "log-1";

const blobPath = (id: string) => `users/${UID}/aircraft/${AC}/blobs/${id}`;
const logPath = (id = LOG) => `users/${UID}/aircraft/${AC}/maintenance_log/${id}`;

function attachment(id: string, type = AttachmentType.ATTACHMENT_TYPE_IMAGE): Attachment {
  return Attachment.fromPartial({ id, name: `${id}.jpg`, type });
}

function logPayload(...attachments: Attachment[]): Buffer {
  return Buffer.from(MaintenanceLog.encode(MaintenanceLog.fromPartial({ id: LOG, attachments })).finish());
}

async function putBlob(id: string) {
  await adminStorage.bucket().file(blobPath(id)).save(Buffer.from([1, 2, 3]));
}

async function blobExists(id: string): Promise<boolean> {
  const [exists] = await adminStorage.bucket().file(blobPath(id)).exists();
  return exists;
}

/** The `deleted: false → true` edge, as the sync engine writes it. */
function deletion(path: string, payload: Buffer, schema = "aircraft.MaintenanceLog", docId = LOG) {
  const before = fft.firestore.makeDocumentSnapshot({ deleted: false, schema, payload }, path);
  const after = fft.firestore.makeDocumentSnapshot({ deleted: true, schema, payload }, path);
  return {
    data: fft.makeChange(before, after),
    params: { uid: UID, acId: AC, kind: "maintenance_log", docId },
  };
}

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.doc(`users/${UID}`));
  await adminStorage.bucket().deleteFiles({ prefix: `users/${UID}/` });
});

describe("onRecordDeleted — a deleted record takes its photos with it (#158)", () => {
  it("deletes the blobs the record owned", async () => {
    // The whole point: deleting a log used to leave its photos in Storage forever. A user who
    // deleted a photo of a damaged part had not deleted it.
    await putBlob("blob-a");
    await putBlob("blob-b");
    const payload = logPayload(attachment("blob-a"), attachment("blob-b"));
    await adminDb.doc(logPath()).set({ deleted: true, schema: "aircraft.MaintenanceLog", payload });

    await wrappedRecord(deletion(logPath(), payload) as never);

    expect(await blobExists("blob-a")).toBe(false);
    expect(await blobExists("blob-b")).toBe(false);
  });

  it("leaves a blob a LIVE record still shows", async () => {
    // Attachment ids are per-attachment, but a copy/duplicate feature can put the same id on two
    // records. Deleting a photo another log still displays is not recoverable.
    await putBlob("shared-blob");
    const payload = logPayload(attachment("shared-blob"));
    await adminDb.doc(logPath("log-live")).set({
      deleted: false,
      schema: "aircraft.MaintenanceLog",
      payload: logPayload(attachment("shared-blob")),
    });

    await wrappedRecord(deletion(logPath(), payload) as never);

    expect(await blobExists("shared-blob")).toBe(true);
  });

  it("ignores LINK attachments — a URL owns no bytes", async () => {
    await putBlob("real-blob");
    const payload = logPayload(
      attachment("real-blob"),
      attachment("just-a-url", AttachmentType.ATTACHMENT_TYPE_LINK),
    );

    await wrappedRecord(deletion(logPath(), payload) as never);

    expect(await blobExists("real-blob")).toBe(false);
  });

  it("collects NOTHING when the deleted payload will not decode", async () => {
    // An unreadable payload is indistinguishable from one that owns every blob in the aircraft.
    // Deleting nothing is the only safe answer.
    await putBlob("blob-a");
    const corrupt = Buffer.from([0xff, 0xff, 0xff, 0xff]);

    await wrappedRecord(deletion(logPath(), corrupt, "aircraft.NotARealSchema") as never);

    expect(await blobExists("blob-a")).toBe(true);
  });

  it("collects NOTHING when a LIVE record will not decode", async () => {
    // We cannot know what the undecodable record still holds, so nothing is safe to collect. A
    // leaked byte is cheap; a deleted photo is not.
    await putBlob("blob-a");
    const payload = logPayload(attachment("blob-a"));
    await adminDb.doc(logPath("log-corrupt")).set({
      deleted: false,
      schema: "aircraft.MaintenanceLog",
      payload: Buffer.from([0xff, 0xff, 0xff, 0xff, 0xff]),
    });

    await wrappedRecord(deletion(logPath(), payload) as never);

    expect(await blobExists("blob-a")).toBe(true);
  });

  it("does nothing on a non-delete write", async () => {
    await putBlob("blob-a");
    const payload = logPayload(attachment("blob-a"));
    const snap = fft.firestore.makeDocumentSnapshot(
      { deleted: false, schema: "aircraft.MaintenanceLog", payload },
      logPath(),
    );

    await wrappedRecord({
      data: fft.makeChange(snap, snap),
      params: { uid: UID, acId: AC, kind: "maintenance_log", docId: LOG },
    } as never);

    expect(await blobExists("blob-a")).toBe(true);
  });

  it("is idempotent — re-running finds the bytes already gone", async () => {
    await putBlob("blob-a");
    const payload = logPayload(attachment("blob-a"));

    await wrappedRecord(deletion(logPath(), payload) as never);
    await wrappedRecord(deletion(logPath(), payload) as never); // no throw

    expect(await blobExists("blob-a")).toBe(false);
  });

  it("decodes a squawk too, not just logs", async () => {
    await putBlob("squawk-blob");
    const payload = Buffer.from(
      Squawk.encode(Squawk.fromPartial({ id: "sq-1", attachments: [attachment("squawk-blob")] })).finish(),
    );
    const before = fft.firestore.makeDocumentSnapshot(
      { deleted: false, schema: "aircraft.Squawk", payload },
      `users/${UID}/aircraft/${AC}/squawk/sq-1`,
    );
    const after = fft.firestore.makeDocumentSnapshot(
      { deleted: true, schema: "aircraft.Squawk", payload },
      `users/${UID}/aircraft/${AC}/squawk/sq-1`,
    );

    await wrappedRecord({
      data: fft.makeChange(before, after),
      params: { uid: UID, acId: AC, kind: "squawk", docId: "sq-1" },
    } as never);

    expect(await blobExists("squawk-blob")).toBe(false);
  });
});

describe("onAircraftDeleted — the aircraft takes all its blobs with it", () => {
  it("deletes the whole blobs/ prefix, no decoding needed", async () => {
    // Blobs are aircraft-scoped, so the prefix dies with the aircraft and "which record owned this?"
    // never has to be asked.
    await putBlob("blob-a");
    await putBlob("blob-b");
    const path = `users/${UID}/aircraft/${AC}`;
    const before = fft.firestore.makeDocumentSnapshot({ deleted: false }, path);
    const after = fft.firestore.makeDocumentSnapshot({ deleted: true }, path);

    await wrappedAircraft({ data: fft.makeChange(before, after), params: { uid: UID, acId: AC } } as never);

    expect(await blobExists("blob-a")).toBe(false);
    expect(await blobExists("blob-b")).toBe(false);
  });
});
