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
 * A record that lets go of a blob frees it (#158; docs/ai/task_population_design.md §8.3).
 *
 * Two edges release blobs:
 *   - **live → deleted.** The tombstoned payload is retained on delete, precisely so it can still be
 *     read, and every blob it names is released. Until #158 nothing did this: `deleteLog()`
 *     tombstoned the row and its photos stayed in Storage forever.
 *   - **live → live, with an attachment removed** (phase B). The released ids are those the payload
 *     named before and no longer names. Until now this edge was left to the daily sweep, after its
 *     grace period. With reference-aware release on the client (T12), the device no longer deletes
 *     remote objects itself, so this is the prompt collection for an edit.
 *
 * A released blob is deleted only if no OTHER live record under the Thing still names it: since
 * phase B one document can sit on several tasks (an AI suggestion's source manual), and a copy can
 * put one id on two records. Anything that will not decode deletes nothing, as before.
 *
 * The client is not asked to cooperate. It could have stamped the blob ids onto the record, but
 * then cleanup would depend on the writing client being new enough to have done so, would miss
 * every record written before the field existed, and would introduce a second source of truth that
 * can drift from the payload it describes. See docs/storage/deletion_gc_design.html §4.
 *
 * [segment] is the entity path segment the event fired for — every path this handler builds must
 * stay in that same tree (see config/entitySegment.ts).
 */
const handleBlobsReleased =
  (segment: EntitySegment) =>
  async (event: FirestoreEvent<Change<DocumentSnapshot> | undefined, Record<string, string>>) => {
    const after = event.data?.after;
    if (after == null || !after.exists) return; // hard-deleted; nothing left to read
    const before = event.data?.before;
    // A creation releases nothing, and neither does any write to a record already tombstoned
    // (a repeated tombstone, or an undelete): the false → true edge already released its blobs.
    if (before == null || !before.exists) return;
    const prev = before.data() as SyncDocWire;
    if (prev?.deleted === true) return;

    const next = after.data() as SyncDocWire;
    const { uid, acId, docId } = event.params;
    const schema = next?.schema ?? "";
    if (!schemaCanOwnBlobs(schema)) return;

    const edge = next.deleted === true ? "deleted" : "edited";
    const released = releasedBlobIds(edge, prev, next, schema);
    if (released == null) {
      // Unreadable. Deleting nothing is the only safe answer: a payload we cannot decode is
      // indistinguishable from one that owns every blob in the Thing.
      logger.error("Could not decode a record that released blobs; skipping", {
        uid, acId, docId, schema, edge,
      });
      return;
    }
    if (released.length === 0) return;

    // `after` is excluded from the scan, but it cannot hold a released id: an edit's released ids
    // are by definition the ones it no longer names, and a tombstone holds no claim.
    const live = await blobsReferencedByLiveRecords(uid, acId, docId, segment);
    if (!live.trustworthy) {
      // A live record would not decode, so we cannot know what it still holds. Collect nothing this
      // run and let the sweep (#159) revisit. A leaked byte is cheap; a deleted photo is not.
      logger.warn("A live record would not decode; collecting nothing this run", { uid, acId, docId });
      return;
    }

    const collectable = released.filter((id) => !live.referenced.has(id));
    if (collectable.length === 0) return;

    await Promise.all(
      collectable.map(async (blobId) => {
        const path = entityBlobPath(uid, acId, blobId, segment);
        try {
          // ignoreNotFound makes this idempotent: the trigger may re-run, and the Thing-delete
          // prefix sweep may have got there first.
          await adminStorage.bucket().file(path).delete({ ignoreNotFound: true });
        } catch (e) {
          logger.error("Blob delete failed", { path, error: String(e) });
        }
      }),
    );

    logger.info("Collected released blobs", { uid, acId, docId, schema, edge, count: collectable.length });
  };

/**
 * The blob ids a write let go of, or null when a payload it needs will not decode. An empty or
 * missing payload on either side of an edit releases nothing rather than everything.
 */
function releasedBlobIds(
  edge: "deleted" | "edited",
  prev: SyncDocWire,
  next: SyncDocWire,
  schema: string,
): string[] | null {
  if (edge === "deleted") {
    return next.payload == null ? [] : blobIdsInPayload(schema, next.payload);
  }
  if (prev.payload == null || next.payload == null) return [];
  // Most writes leave the attachments alone; the stored base64 string compares cheaply.
  if (typeof prev.payload === "string" && prev.payload === next.payload) return [];
  const had = blobIdsInPayload(prev.schema ?? schema, prev.payload);
  const has = blobIdsInPayload(schema, next.payload);
  if (had == null || has == null) return null;
  const kept = new Set(has);
  return had.filter((id) => !kept.has(id));
}

// MIGRATION (task F3): the `aircraft`-path registration is gone — see onThingDeleted.
// MIGRATION (thing_migration_design.md §2.7c / task B9): deployed with C2, NOT with the Phase A/B
// branch. A cutover copy CREATES each document, so `before` never exists and every record the Phase
// D script copies would read as a fresh authored write here. By C2 the copy is done, and nothing
// writes `/thing/` until E2 anyway — so these are inert before this point and correct after it.
//
// Was `onThingRecordDeleted`, which handled only the delete edge (phase B renamed it). One trigger
// for both edges, on the same path, so a write is judged once.
export const onThingRecordBlobsReleased = onDocumentWritten(
  {
    document: `users/{uid}/${ENTITY_SEGMENT_THING}/{acId}/{kind}/{docId}`,
    region: FUNCTION_REGION,
  },
  handleBlobsReleased(ENTITY_SEGMENT_THING),
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
