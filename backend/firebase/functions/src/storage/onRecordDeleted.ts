import { logger } from "firebase-functions/v2";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb, adminStorage } from "../config/firebaseAdmin.js";
import { blobIdsInPayload, schemaCanOwnBlobs } from "./blobRefs.js";

/** Envelope fields the sync engine writes. `payload` is proto bytes; `schema` names its type. */
type SyncDocWire = {
  payload?: Uint8Array | Buffer;
  schema?: string;
  deleted?: boolean;
};

/**
 * Deleting a record deletes its attachments (#158).
 *
 * Until now it did not: `deleteLog()` tombstoned the row and the photos it carried stayed in Storage
 * forever. Two costs — bytes we pay for indefinitely, and a user who deleted a photo of a damaged
 * part had not actually deleted it.
 *
 * Fires on the `deleted: false → true` edge, decodes the tombstoned payload (retained on delete,
 * precisely so it can still be read), and collects the blobs it names.
 *
 * The client is not asked to cooperate. It could have stamped the blob ids onto the tombstone, but
 * then cleanup would depend on the deleting client being new enough to have done so, would miss
 * every record deleted before the field existed, and would introduce a second source of truth that
 * can drift from the payload it describes — and a drifted list either leaks bytes or deletes a photo
 * a live record still shows. See docs/storage/deletion_gc_design.html §4.
 */
export const onRecordDeleted = onDocumentWritten(
  { document: "users/{uid}/aircraft/{acId}/{kind}/{docId}", region: FUNCTION_REGION },
  async (event) => {
    const after = event.data?.after;
    if (after == null || !after.exists) return; // hard-deleted; nothing left to read

    const before = event.data?.before;
    const wasDeleted = before?.exists === true && (before.data() as SyncDocWire)?.deleted === true;
    const doc = after.data() as SyncDocWire;
    if (doc?.deleted !== true || wasDeleted) return; // only the false → true edge

    const { uid, acId, docId } = event.params;
    const schema = doc.schema ?? "";
    if (!schemaCanOwnBlobs(schema) || doc.payload == null) return;

    const owned = blobIdsInPayload(schema, toBytes(doc.payload));
    if (owned == null) {
      // Unreadable. Deleting nothing is the only safe answer: a payload we cannot decode is
      // indistinguishable from one that owns every blob in the aircraft.
      logger.error("Could not decode a deleted record; skipping blob cleanup", {
        uid, acId, docId, schema,
      });
      return;
    }
    if (owned.length === 0) return;

    const live = await blobsReferencedByLiveRecords(uid, acId, docId);
    if (!live.trustworthy) {
      // A live record would not decode, so we cannot know what it still holds. Collect nothing this
      // run and let the sweep (#159) revisit. A leaked byte is cheap; a deleted photo is not.
      logger.warn("A live record would not decode; collecting nothing this run", { uid, acId, docId });
      return;
    }

    // Never delete a blob a LIVE record still shows. Attachment ids are per-attachment, but a copy
    // or duplicate feature can put the same id on two records, and deleting a photo another log
    // still displays is not recoverable.
    const collectable = owned.filter((id) => !live.referenced.has(id));
    if (collectable.length === 0) return;

    await Promise.all(
      collectable.map(async (blobId) => {
        const path = `users/${uid}/aircraft/${acId}/blobs/${blobId}`;
        try {
          // ignoreNotFound makes this idempotent: the trigger may re-run, and the aircraft-delete
          // prefix sweep may have got there first.
          await adminStorage.bucket().file(path).delete({ ignoreNotFound: true });
        } catch (e) {
          logger.error("Blob delete failed", { path, error: String(e) });
        }
      }),
    );

    logger.info("Collected blobs for a deleted record", {
      uid, acId, docId, schema, count: collectable.length,
    });
  },
);

type LiveRefs = {
  referenced: Set<string>;
  /** False when a live record could not be decoded — its claims are unknown, so nothing is safe. */
  trustworthy: boolean;
};

/**
 * Blob ids still referenced by records that are NOT deleted, excluding the one being processed.
 *
 * Scoped to the aircraft, which is also the blob namespace: a blob under this aircraft can only be
 * referenced by a record under this aircraft, so nothing outside it needs reading.
 */
async function blobsReferencedByLiveRecords(
  uid: string,
  acId: string,
  excludeDocId: string,
): Promise<LiveRefs> {
  const referenced = new Set<string>();
  const collections = await adminDb.doc(`users/${uid}/aircraft/${acId}`).listCollections();

  for (const collection of collections) {
    const snap = await collection.get();
    for (const record of snap.docs) {
      if (record.id === excludeDocId) continue;
      const data = record.data() as SyncDocWire;
      if (data?.deleted === true) continue; // a tombstone holds no claim on the bytes

      const schema = data?.schema ?? "";
      if (!schemaCanOwnBlobs(schema) || data.payload == null) continue;

      const ids = blobIdsInPayload(schema, toBytes(data.payload));
      if (ids == null) {
        logger.warn("Could not decode a live record", { uid, acId, docId: record.id, schema });
        return { referenced, trustworthy: false };
      }
      ids.forEach((id) => referenced.add(id));
    }
  }
  return { referenced, trustworthy: true };
}

function toBytes(payload: Uint8Array | Buffer): Uint8Array {
  return payload instanceof Uint8Array ? payload : new Uint8Array(payload);
}
