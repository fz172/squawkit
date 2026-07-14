import { Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";

import { adminDb, adminStorage } from "../config/firebaseAdmin.js";
import { blobIdsInPayload, schemaCanOwnBlobs } from "./blobRefs.js";

/**
 * The scheduled storage sweep (#159). Two jobs, over every user's tree:
 *
 *   1. **Purge expired tombstones.** Nothing else does. Every record a user has ever deleted still
 *      sits in Firestore with its payload intact, growing without bound.
 *   2. **Collect orphaned blobs.** The backstop for bytes whose owning record is gone — including
 *      the entire pre-existing backlog, which is reachable precisely because the server can decode a
 *      payload (docs/storage/deletion_gc_design.html §3).
 *
 * Both are destructive in ways that cannot be undone: purge a tombstone too early and a deleted
 * record RESURRECTS; delete a blob too eagerly and a user's photo is gone. Hence [SweepOptions.dryRun],
 * which defaults on, and the two grace windows.
 */

export type SweepOptions = {
  dryRun: boolean;
  tombstoneRetentionDays: number;
  orphanGraceDays: number;
  /** Limit to one user. Useful for a rehearsal on a single account before sweeping everyone. */
  onlyUid?: string;
};

export type SweepReport = {
  dryRun: boolean;
  usersScanned: number;
  tombstonesPurged: number;
  orphanBlobsCollected: number;
  /** Aircraft skipped because a live record would not decode — see [collectOrphanBlobs]. */
  aircraftSkipped: number;
};

type SyncDocWire = {
  payload?: Uint8Array | Buffer;
  schema?: string;
  deleted?: boolean;
  lastUpdateTimestamp?: Timestamp;
};

export async function runStorageSweep(options: SweepOptions): Promise<SweepReport> {
  const report: SweepReport = {
    dryRun: options.dryRun,
    usersScanned: 0,
    tombstonesPurged: 0,
    orphanBlobsCollected: 0,
    aircraftSkipped: 0,
  };

  const users = options.onlyUid
    ? [adminDb.doc(`users/${options.onlyUid}`)]
    : await adminDb.collection("users").listDocuments();

  for (const userRef of users) {
    report.usersScanned++;
    const uid = userRef.id;

    // Blobs first, tombstones second. A tombstone is what tells the blob collector that a record is
    // dead; purge it first and the blobs it owned become unattributable orphans in the same run.
    await collectOrphanBlobs(uid, options, report);
    await purgeExpiredTombstones(uid, options, report);
  }

  logger.info(options.dryRun ? "Storage sweep (DRY RUN)" : "Storage sweep", { ...report });
  return report;
}

/**
 * Hard-delete tombstones older than the retention window, everywhere in the user's tree.
 *
 * The window is a correctness constraint: an offline device that still holds the live row will, on
 * reconnect, find no tombstone and push the record back up as a fresh write. Deleting a tombstone
 * too early does not lose data — it RESURRECTS it, which is worse, because the user believes it is
 * gone.
 */
async function purgeExpiredTombstones(
  uid: string,
  options: SweepOptions,
  report: SweepReport,
): Promise<void> {
  const cutoff = Timestamp.fromMillis(
    Date.now() - options.tombstoneRetentionDays * 24 * 60 * 60 * 1000,
  );

  for (const collection of await adminDb.doc(`users/${uid}`).listCollections()) {
    for (const doc of (await collection.get()).docs) {
      await purgeIfExpired(doc.ref, doc.data() as SyncDocWire, cutoff, options, report);

      // Per-aircraft records live one level down.
      for (const child of await doc.ref.listCollections()) {
        for (const record of (await child.get()).docs) {
          await purgeIfExpired(record.ref, record.data() as SyncDocWire, cutoff, options, report);
        }
      }
    }
  }
}

async function purgeIfExpired(
  ref: FirebaseFirestore.DocumentReference,
  data: SyncDocWire,
  cutoff: Timestamp,
  options: SweepOptions,
  report: SweepReport,
): Promise<void> {
  if (data?.deleted !== true) return;

  const stamped = data.lastUpdateTimestamp;
  // No timestamp means we cannot age it. Keep it: an un-ageable tombstone is a rounding error in
  // cost, and deleting one we cannot date risks resurrecting the record it buries.
  if (stamped == null || stamped.toMillis() > cutoff.toMillis()) return;

  report.tombstonesPurged++;
  if (!options.dryRun) await ref.delete();
}

/**
 * Delete blobs no live record references.
 *
 * "Referenced" is answered exactly, not guessed: the payloads ARE the reference list, and the server
 * can decode them. Scoped per aircraft, which is also the blob namespace.
 *
 * Two refusals, both deliberate:
 *
 * - **A live record that will not decode skips the whole aircraft.** We cannot know what it still
 *   holds, and a blob wrongly judged unreferenced is a user's photo deleted for good.
 * - **A blob younger than the grace window is left alone.** Records and blobs travel in separate
 *   queues, so a freshly uploaded photo can arrive before the log that references it. Without the
 *   window we would delete the picture the user just took.
 */
async function collectOrphanBlobs(
  uid: string,
  options: SweepOptions,
  report: SweepReport,
): Promise<void> {
  const aircraftRefs = await adminDb.collection(`users/${uid}/aircraft`).listDocuments();
  const graceCutoffMs = Date.now() - options.orphanGraceDays * 24 * 60 * 60 * 1000;

  for (const aircraftRef of aircraftRefs) {
    const acId = aircraftRef.id;
    const referenced = await blobsReferencedByLiveRecords(uid, acId);
    if (referenced == null) {
      report.aircraftSkipped++;
      logger.warn("Skipping an aircraft: a live record would not decode", { uid, acId });
      continue;
    }

    const [files] = await adminStorage
      .bucket()
      .getFiles({ prefix: `users/${uid}/aircraft/${acId}/blobs/` });

    for (const file of files) {
      const blobId = file.name.split("/").pop() ?? "";
      if (blobId.length === 0 || referenced.has(blobId)) continue;

      const createdMs = Date.parse(String(file.metadata.timeCreated ?? ""));
      if (Number.isFinite(createdMs) && createdMs > graceCutoffMs) continue; // too young to judge

      report.orphanBlobsCollected++;
      if (!options.dryRun) await file.delete({ ignoreNotFound: true });
    }
  }
}

/** Blob ids held by NOT-deleted records in this aircraft, or `null` if any of them will not decode. */
async function blobsReferencedByLiveRecords(
  uid: string,
  acId: string,
): Promise<Set<string> | null> {
  const referenced = new Set<string>();

  for (const collection of await adminDb.doc(`users/${uid}/aircraft/${acId}`).listCollections()) {
    for (const record of (await collection.get()).docs) {
      const data = record.data() as SyncDocWire;
      if (data?.deleted === true) continue; // a tombstone holds no claim on the bytes

      const schema = data?.schema ?? "";
      if (!schemaCanOwnBlobs(schema) || data.payload == null) continue;

      const ids = blobIdsInPayload(schema, toBytes(data.payload));
      if (ids == null) return null; // unknowable — the caller must skip this aircraft entirely
      ids.forEach((id) => referenced.add(id));
    }
  }
  return referenced;
}

function toBytes(payload: Uint8Array | Buffer): Uint8Array {
  return payload instanceof Uint8Array ? payload : new Uint8Array(payload);
}
