import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage, fft } from "./helpers.js";

import { Attachment, AttachmentType } from "../src/generated/proto/thing/attachment.js";
import { DataLog } from "../src/generated/proto/datalog/data_log.js";
import { MaintenanceLog } from "../src/generated/proto/thing/maintenance_log.js";
import { Squawk } from "../src/generated/proto/thing/squawk.js";
import { onThingDeleted } from "../src/sharing/onThingDeleted.js";
import { onThingRecordBlobsReleased } from "../src/storage/onRecordBlobsReleased.js";

const wrappedRecord = fft.wrap(onThingRecordBlobsReleased);
const wrappedThing = fft.wrap(onThingDeleted);

const UID = "user-gc";
const AC = "ac-gc";
const LOG = "log-1";

const blobPath = (id: string) => `users/${UID}/thing/${AC}/blobs/${id}`;
const logPath = (id = LOG) => `users/${UID}/thing/${AC}/maintenance_log/${id}`;

function attachment(id: string, type = AttachmentType.ATTACHMENT_TYPE_IMAGE): Attachment {
  return Attachment.fromPartial({ id, name: `${id}.jpg`, type });
}

/**
 * A payload in the shape the CLIENT actually writes: **base64 text**, not bytes.
 *
 * These fixtures used to build Buffers, a shape production never produces, so every test here
 * passed against a decoder that could not read a real document. That is #428, and it is the reason
 * the sweep deleted real photos before it was caught there.
 */
function logPayload(...attachments: Attachment[]): string {
  return Buffer.from(
    MaintenanceLog.encode(MaintenanceLog.fromPartial({ id: LOG, attachments })).finish(),
  ).toString("base64");
}

const DATA_LOG = "dl-1";
const dataLogPath = (id = DATA_LOG) => `users/${UID}/thing/${AC}/data_log/${id}`;

/** A DataLog record whose raw file lives in the blob [blobId] (data log design §4.1). */
function dataLogPayload(blobId: string): string {
  return Buffer.from(
    DataLog.encode(
      DataLog.fromPartial({
        id: { value: DATA_LOG },
        rawFile: attachment(blobId, AttachmentType.ATTACHMENT_TYPE_FILE),
      }),
    ).finish(),
  ).toString("base64");
}

/** Base64 of bytes that are not a valid message of any schema we know. */
const CORRUPT_PAYLOAD = Buffer.from([0xff, 0xff, 0xff, 0xff]).toString("base64");

async function putBlob(id: string) {
  await adminStorage.bucket().file(blobPath(id)).save(Buffer.from([1, 2, 3]));
}

async function blobExists(id: string): Promise<boolean> {
  const [exists] = await adminStorage.bucket().file(blobPath(id)).exists();
  return exists;
}

/** The `deleted: false → true` edge, as the sync engine writes it. */
function deletion(path: string, payload: unknown, schema = "aircraft.MaintenanceLog", docId = LOG) {
  const before = fft.firestore.makeDocumentSnapshot({ deleted: false, schema, payload }, path);
  const after = fft.firestore.makeDocumentSnapshot({ deleted: true, schema, payload }, path);
  return {
    data: fft.makeChange(before, after),
    params: { uid: UID, acId: AC, kind: "maintenance_log", docId },
  };
}

/** A live → live write, as the sync engine pushes an edit. */
function edit(path: string, before: unknown, after: unknown, docId = LOG) {
  const schema = "aircraft.MaintenanceLog";
  return {
    data: fft.makeChange(
      fft.firestore.makeDocumentSnapshot({ deleted: false, schema, payload: before }, path),
      fft.firestore.makeDocumentSnapshot({ deleted: false, schema, payload: after }, path),
    ),
    params: { uid: UID, acId: AC, kind: "maintenance_log", docId },
  };
}

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.doc(`users/${UID}`));
  await adminStorage.bucket().deleteFiles({ prefix: `users/${UID}/` });
});

describe("onRecordBlobsReleased — a deleted record takes its photos with it (#158)", () => {
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

  it("a deleted data log takes its raw file with it", async () => {
    await putBlob("dl-blob");
    const payload = dataLogPayload("dl-blob");

    await wrappedRecord(
      deletion(dataLogPath(), payload, "datalog.DataLog", DATA_LOG) as never,
    );

    expect(await blobExists("dl-blob")).toBe(false);
  });

  it("a deleted log never reclaims the data log it referenced", async () => {
    // A DATA_LOG attachment is a reference: the DataLog record owns the bytes and outlives the log
    // entry that pointed at it (data log design §4.2).
    await putBlob("dl-blob");
    const ref = Attachment.fromPartial({
      id: "dl-blob",
      name: "flight.csv",
      type: AttachmentType.ATTACHMENT_TYPE_DATA_LOG,
      dataLogId: { value: DATA_LOG },
    });
    const payload = logPayload(ref);

    await wrappedRecord(deletion(logPath(), payload) as never);

    expect(await blobExists("dl-blob")).toBe(true);
  });

  it("collects NOTHING when the deleted payload will not decode", async () => {
    // An unreadable payload is indistinguishable from one that owns every blob in the aircraft.
    // Deleting nothing is the only safe answer.
    await putBlob("blob-a");

    await wrappedRecord(deletion(logPath(), CORRUPT_PAYLOAD, "aircraft.NotARealSchema") as never);

    expect(await blobExists("blob-a")).toBe(true);
  });

  /**
   * The #428 regression, on this trigger. A payload whose SHAPE cannot be read must be treated as
   * unknowable, never as an empty record — an empty record owns no attachments, and "owns no
   * attachments" is one branch away from "delete everything it pointed at".
   */
  it("collects NOTHING when the deleted payload is not a shape it can read", async () => {
    await putBlob("blob-a");

    await wrappedRecord(deletion(logPath(), 12345) as never); // neither base64 text nor bytes

    expect(await blobExists("blob-a")).toBe(true);
  });

  it("collects NOTHING for an empty payload rather than reading it as 'owns nothing'", async () => {
    await putBlob("blob-a");

    await wrappedRecord(deletion(logPath(), "") as never);

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
      payload: CORRUPT_PAYLOAD,
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
    ).toString("base64");
    const before = fft.firestore.makeDocumentSnapshot(
      { deleted: false, schema: "aircraft.Squawk", payload },
      `users/${UID}/thing/${AC}/squawk/sq-1`,
    );
    const after = fft.firestore.makeDocumentSnapshot(
      { deleted: true, schema: "aircraft.Squawk", payload },
      `users/${UID}/thing/${AC}/squawk/sq-1`,
    );

    await wrappedRecord({
      data: fft.makeChange(before, after),
      params: { uid: UID, acId: AC, kind: "squawk", docId: "sq-1" },
    } as never);

    expect(await blobExists("squawk-blob")).toBe(false);
  });
});

describe("onRecordBlobsReleased — an edit that drops an attachment frees it (phase B)", () => {
  it("collects the blob an edit removed, and keeps the ones it still names", async () => {
    await putBlob("kept");
    await putBlob("dropped");

    await wrappedRecord(
      edit(logPath(), logPayload(attachment("kept"), attachment("dropped")), logPayload(attachment("kept"))) as never,
    );

    expect(await blobExists("kept")).toBe(true);
    expect(await blobExists("dropped")).toBe(false);
  });

  it("spares a dropped blob another LIVE record still names (a shared document)", async () => {
    await putBlob("manual");
    await adminDb.doc(logPath("log-2")).set({
      deleted: false,
      schema: "aircraft.MaintenanceLog",
      payload: logPayload(attachment("manual")),
    });

    await wrappedRecord(edit(logPath(), logPayload(attachment("manual")), logPayload()) as never);

    expect(await blobExists("manual")).toBe(true);
  });

  it("releases nothing when an edit adds an attachment or changes nothing else", async () => {
    await putBlob("a");
    await putBlob("b");

    await wrappedRecord(edit(logPath(), logPayload(attachment("a")), logPayload(attachment("a"), attachment("b"))) as never);
    await wrappedRecord(edit(logPath(), logPayload(attachment("a")), logPayload(attachment("a"))) as never);

    expect(await blobExists("a")).toBe(true);
    expect(await blobExists("b")).toBe(true);
  });

  it("collects NOTHING when either side of the edit will not decode", async () => {
    await putBlob("a");

    await wrappedRecord(edit(logPath(), CORRUPT_PAYLOAD, logPayload()) as never);
    await wrappedRecord(edit(logPath(), logPayload(attachment("a")), CORRUPT_PAYLOAD) as never);

    expect(await blobExists("a")).toBe(true);
  });

  it("releases nothing on a creation, an undelete, or a write to a tombstone", async () => {
    await putBlob("a");
    const schema = "aircraft.MaintenanceLog";
    const live = fft.firestore.makeDocumentSnapshot({ deleted: false, schema, payload: logPayload() }, logPath());
    const tombstone = fft.firestore.makeDocumentSnapshot(
      { deleted: true, schema, payload: logPayload(attachment("a")) },
      logPath(),
    );
    const missing = fft.firestore.makeDocumentSnapshot({}, logPath());
    const params = { uid: UID, acId: AC, kind: "maintenance_log", docId: LOG };

    await wrappedRecord({ data: fft.makeChange(missing, live), params } as never);
    await wrappedRecord({ data: fft.makeChange(tombstone, live), params } as never);
    await wrappedRecord({ data: fft.makeChange(tombstone, tombstone), params } as never);

    expect(await blobExists("a")).toBe(true);
  });
});

describe("onThingDeleted — the aircraft takes all its blobs with it", () => {
  it("deletes the whole blobs/ prefix, no decoding needed", async () => {
    // Blobs are aircraft-scoped, so the prefix dies with the aircraft and "which record owned this?"
    // never has to be asked.
    await putBlob("blob-a");
    await putBlob("blob-b");
    const path = `users/${UID}/thing/${AC}`;
    const before = fft.firestore.makeDocumentSnapshot({ deleted: false }, path);
    const after = fft.firestore.makeDocumentSnapshot({ deleted: true }, path);

    await wrappedThing({ data: fft.makeChange(before, after), params: { uid: UID, acId: AC } } as never);

    expect(await blobExists("blob-a")).toBe(false);
    expect(await blobExists("blob-b")).toBe(false);
  });
});
