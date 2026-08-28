import type { DocumentSnapshot } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import type { Change, FirestoreEvent } from "firebase-functions/v2/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

import { FUNCTION_REGION } from "../config/env.js";
import {
  ENTITY_SEGMENT_LEGACY,
  ENTITY_SEGMENT_THING,
  entityBlobPath,
  entityDocPath,
  type EntitySegment,
} from "../config/entitySegment.js";
import { adminDb, adminStorage } from "../config/firebaseAdmin.js";
import { blobIdsInPayload, schemaCanOwnBlobs } from "./blobRefs.js";

/** Envelope fields the sync engine writes. `schema` names the type `payload` decodes to. */
type SyncDocWire = {
  /**
   * **base64, not bytes.** `FirestoreSyncWriter` stores this as a base64 STRING; it was typed here
   * as binary, and `record.data() as SyncDocWire` asserts rather than checks, so nothing caught it.
   * `blobIdsInPayload` now takes the stored shape and does the conversion itself. See #428.
   */
  payload?: string | Uint8Array | Buffer;
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
 *
 * [segment] is the entity path segment the event fired for — every path this handler builds must
 * stay in that same tree (see config/entitySegment.ts).
 */
const handleRecordDeleted =
  (segment: EntitySegment) =>
  async (event: FirestoreEvent<Change<DocumentSnapshot> | undefined, Record<string, string>>) => {
    const after = event.data?.after;
    if (after == null || !after.exists) return; // hard-deleted; nothing left to read

    const before = event.data?.before;
    const wasDeleted = before?.exists === true && (before.data() as SyncDocWire)?.deleted === true;
    const doc = after.data() as SyncDocWire;
    if (doc?.deleted !== true || wasDeleted) return; // only the false → true edge

    const { uid, acId, docId } = event.params;
    const schema = doc.schema ?? "";
    if (!schemaCanOwnBlobs(schema) || doc.payload == null) return;

    const owned = blobIdsInPayload(schema, doc.payload);
    if (owned == null) {
      // Unreadable. Deleting nothing is the only safe answer: a payload we cannot decode is
      // indistinguishable from one that owns every blob in the aircraft.
      logger.error("Could not decode a deleted record; skipping blob cleanup", {
        uid, acId, docId, schema,
      });
      return;
    }
    if (owned.length === 0) return;

    const live = await blobsReferencedByLiveRecords(uid, acId, docId, segment);
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
        const path = entityBlobPath(uid, acId, blobId, segment);
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
  };

/**
 * MIGRATION (thing_migration_design.md §2.7 / task B9): registered twice, once per entity segment —
 * a v2 Firestore trigger path is a deploy-time literal with no "either segment" wildcard, and a
 * deploy is global. `onRecordDeleted` keeps its export name so the already-deployed function is not
 * torn down and recreated; Phase F3 deletes it.
 */
export const onRecordDeleted = onDocumentWritten(
  {
    document: `users/{uid}/${ENTITY_SEGMENT_LEGACY}/{acId}/{kind}/{docId}`,
    region: FUNCTION_REGION,
  },
  handleRecordDeleted(ENTITY_SEGMENT_LEGACY),
);

// MIGRATION (thing_migration_design.md §2.7c / task B9): the `/thing/` registration is NOT deployed
// with this branch. A cutover copy CREATES each document, so `before` never exists — which means
// every record the Phase D script copies would look like a brand-new write to these handlers. See
// §2.7c for what that costs. The registrations live on `feat/thing-migration-checkpoint-2` and go
// out with C2, after the copy is done and before any client writes `/thing/` at E2.

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
  segment: EntitySegment,
): Promise<LiveRefs> {
  const referenced = new Set<string>();
  const collections = await adminDb.doc(entityDocPath(uid, acId, segment)).listCollections();

  for (const collection of collections) {
    const snap = await collection.get();
    for (const record of snap.docs) {
      if (record.id === excludeDocId) continue;
      const data = record.data() as SyncDocWire;
      if (data?.deleted === true) continue; // a tombstone holds no claim on the bytes

      const schema = data?.schema ?? "";
      if (!schemaCanOwnBlobs(schema) || data.payload == null) continue;

      const ids = blobIdsInPayload(schema, data.payload);
      if (ids == null) {
        logger.warn("Could not decode a live record", { uid, acId, docId: record.id, schema });
        return { referenced, trustworthy: false };
      }
      ids.forEach((id) => referenced.add(id));
    }
  }
  return { referenced, trustworthy: true };
}
