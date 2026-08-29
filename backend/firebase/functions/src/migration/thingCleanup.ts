import { createHash } from "node:crypto";

import type { Bucket } from "@google-cloud/storage";

import { ENTITY_SEGMENT_LEGACY, ENTITY_SEGMENT_THING } from "../config/entitySegment.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { preflight, type CutoverOptions } from "./thingCutover.js";

/**
 * The deferred deletion pass (docs/product/thing_migration_design.md §7, task B4).
 *
 * The counterpart to `thingCutover.ts`, and deliberately a separate program rather than a flag on
 * it: the cutover's central safety property is that it never deletes, which is what makes re-running
 * it free. Folding deletion in would spend that property. This runs later, on its own decision.
 *
 * **What actually authorises a delete here is re-verification, not a timestamp.** The 7-day grace
 * window is about devices picking up the new build and about the operator having time to notice a
 * problem — it is human judgement, asserted with `--grace-elapsed`. It is NOT evidence that the copy
 * succeeded. So before removing anything, this re-proves the copy *from scratch*, per aircraft:
 * every source document must have a counterpart at `/thing/`, and every source blob must have a
 * byte-identical counterpart. Anything that fails is skipped and reported, never deleted.
 *
 * That ordering matters. A timestamp says "we believed this worked a week ago." Re-verification says
 * "it is true right now." Only the second one is worth deleting a user's photos on.
 */

export type CleanupOptions = {
  /** Report what would be deleted; delete nothing. */
  dryRun: boolean;
  /** Restrict the run to these uids. Empty/absent means every account. */
  onlyUids?: readonly string[];
  /** Cloud Storage bucket. Same resolution problem as the cutover — see `CutoverOptions`. */
  bucketName?: string;
  /**
   * The operator's assertion that §7's 7-day grace window has elapsed since the verified copy.
   *
   * Required for a live run. There is no stored per-account copy timestamp to check this against —
   * the cutover records none — so it cannot be enforced in code, and pretending otherwise with a
   * derived date would be worse than asking plainly.
   */
  graceElapsed: boolean;
  /**
   * Compare blobs by size only instead of size + sha256. Strongly discouraged here: this is the
   * program that makes the destination the only copy.
   */
  skipChecksum?: boolean;
};

export type SkippedAircraft = {
  uid: string;
  acId: string;
  reason: string;
};

export type CleanupUidOutcome = {
  uid: string;
  ok: boolean;
  error?: string;
  aircraftDeleted: number;
  aircraftSkipped: number;
  documentsDeleted: number;
  blobsDeleted: number;
  bytesReclaimed: number;
};

export type CleanupReport = {
  dryRun: boolean;
  startedAtMs: number;
  finishedAtMs: number;
  succeeded: string[];
  failed: { uid: string; error: string }[];
  /** Every aircraft left in place, and why. Read this before re-running. */
  skipped: SkippedAircraft[];
  outcomes: CleanupUidOutcome[];
  totals: Omit<CleanupUidOutcome, "uid" | "ok" | "error">;
};

function emptyOutcome(uid: string): CleanupUidOutcome {
  return {
    uid,
    ok: true,
    aircraftDeleted: 0,
    aircraftSkipped: 0,
    documentsDeleted: 0,
    blobsDeleted: 0,
    bytesReclaimed: 0,
  };
}

function sha256(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

export async function runThingCleanup(options: CleanupOptions): Promise<CleanupReport> {
  if (!options.dryRun && !options.graceElapsed) {
    // A refusal rather than a warning. The window exists so a problem discovered on day three is
    // still recoverable by pointing clients back at the old paths; deleting early removes the only
    // thing that makes that recovery possible.
    throw new Error(
      "Refusing to delete: the §7 grace window has not been asserted. Pass graceElapsed once 7 days " +
        "have passed since the verified copy (D3), or use dryRun to preview.",
    );
  }

  const startedAtMs = Date.now();
  const bucket = await preflight(options as CutoverOptions);

  const uids =
    options.onlyUids != null && options.onlyUids.length > 0
      ? [...options.onlyUids]
      : (await adminDb.collection("users").listDocuments()).map((ref) => ref.id);

  const outcomes: CleanupUidOutcome[] = [];
  const skipped: SkippedAircraft[] = [];

  for (const uid of uids) {
    const outcome = emptyOutcome(uid);
    try {
      await cleanUid(uid, bucket, options, outcome, skipped);
    } catch (e) {
      outcome.ok = false;
      outcome.error = String(e);
    }
    outcomes.push(outcome);
  }

  const totals = outcomes.reduce(
    (acc, o) => ({
      aircraftDeleted: acc.aircraftDeleted + o.aircraftDeleted,
      aircraftSkipped: acc.aircraftSkipped + o.aircraftSkipped,
      documentsDeleted: acc.documentsDeleted + o.documentsDeleted,
      blobsDeleted: acc.blobsDeleted + o.blobsDeleted,
      bytesReclaimed: acc.bytesReclaimed + o.bytesReclaimed,
    }),
    {
      aircraftDeleted: 0,
      aircraftSkipped: 0,
      documentsDeleted: 0,
      blobsDeleted: 0,
      bytesReclaimed: 0,
    },
  );

  return {
    dryRun: options.dryRun,
    startedAtMs,
    finishedAtMs: Date.now(),
    succeeded: outcomes.filter((o) => o.ok).map((o) => o.uid),
    failed: outcomes
      .filter((o) => !o.ok)
      .map((o) => ({ uid: o.uid, error: o.error ?? "unknown" })),
    skipped,
    outcomes,
    totals,
  };
}

async function cleanUid(
  uid: string,
  bucket: Bucket,
  options: CleanupOptions,
  outcome: CleanupUidOutcome,
  skipped: SkippedAircraft[],
): Promise<void> {
  const aircraftRefs = await adminDb
    .collection(`users/${uid}/${ENTITY_SEGMENT_LEGACY}`)
    .listDocuments();

  for (const aircraftRef of aircraftRefs) {
    const acId = aircraftRef.id;
    const failure = await verifyMigrated(uid, acId, bucket, options);
    if (failure != null) {
      // Leave it exactly where it is. An aircraft that cannot be proven copied is an aircraft whose
      // only intact copy may be the one we are looking at.
      outcome.aircraftSkipped++;
      skipped.push({ uid, acId, reason: failure });
      continue;
    }
    await deleteAircraft(uid, acId, aircraftRef, bucket, options, outcome);
    outcome.aircraftDeleted++;
  }
}

/**
 * Re-prove the copy for one aircraft. Returns `null` when it is safe to delete, or the reason it is
 * not.
 *
 * Deliberately re-derived from the live data rather than read from any record of the earlier run.
 * Every check below asks "is the destination complete *now*", because that is the only question
 * whose answer justifies deleting the source.
 */
async function verifyMigrated(
  uid: string,
  acId: string,
  bucket: Bucket,
  options: CleanupOptions,
): Promise<string | null> {
  const sourceDoc = adminDb.doc(`users/${uid}/${ENTITY_SEGMENT_LEGACY}/${acId}`);
  const destDoc = adminDb.doc(`users/${uid}/${ENTITY_SEGMENT_THING}/${acId}`);

  const [sourceSnap, destSnap] = await Promise.all([sourceDoc.get(), destDoc.get()]);
  if (sourceSnap.exists && !destSnap.exists) {
    return `no counterpart at /${ENTITY_SEGMENT_THING}/${acId}`;
  }

  // Every source record must be present at the destination. Compared by ID, not by count: equal
  // counts with different ids would pass a count check and still have lost data.
  for (const collection of await sourceDoc.listCollections()) {
    const sourceIds = (await collection.get()).docs.map((d) => d.id);
    if (sourceIds.length === 0) continue;
    const destIds = new Set(
      (await destDoc.collection(collection.id).get()).docs.map((d) => d.id),
    );
    const missing = sourceIds.filter((id) => !destIds.has(id));
    if (missing.length > 0) {
      return `${missing.length} of ${sourceIds.length} ${collection.id} documents missing at destination (e.g. ${missing[0]})`;
    }
  }

  const sourcePrefix = `users/${uid}/${ENTITY_SEGMENT_LEGACY}/${acId}/blobs/`;
  const [sourceBlobs] = await bucket.getFiles({ prefix: sourcePrefix });
  for (const file of sourceBlobs) {
    const blobId = file.name.slice(sourcePrefix.length);
    if (blobId.length === 0) continue;
    const dest = bucket.file(
      `users/${uid}/${ENTITY_SEGMENT_THING}/${acId}/blobs/${blobId}`,
    );
    const [exists] = await dest.exists();
    if (!exists) return `blob ${blobId} missing at destination`;

    const [sourceBytes] = await file.download();
    const [destBytes] = await dest.download();
    if (sourceBytes.byteLength !== destBytes.byteLength) {
      return `blob ${blobId} differs in size (${sourceBytes.byteLength} vs ${destBytes.byteLength})`;
    }
    if (options.skipChecksum !== true && sha256(sourceBytes) !== sha256(destBytes)) {
      return `blob ${blobId} differs by checksum`;
    }
  }

  return null;
}

/** Delete one aircraft's old Firestore subtree and its old Storage objects. */
async function deleteAircraft(
  uid: string,
  acId: string,
  sourceDoc: FirebaseFirestore.DocumentReference,
  bucket: Bucket,
  options: CleanupOptions,
  outcome: CleanupUidOutcome,
): Promise<void> {
  for (const collection of await sourceDoc.listCollections()) {
    outcome.documentsDeleted += (await collection.count().get()).data().count;
  }
  // Only if there IS one. `listDocuments()` returns references for documents that do not exist but
  // still have subcollections — an aircraft whose own doc was already deleted, with orphaned child
  // records underneath. Counting those inflated the report against the cutover's own figures, which
  // is exactly the kind of unexplained discrepancy that makes an operator distrust a delete.
  if ((await sourceDoc.get()).exists) outcome.documentsDeleted++;

  const sourcePrefix = `users/${uid}/${ENTITY_SEGMENT_LEGACY}/${acId}/blobs/`;
  const [sourceBlobs] = await bucket.getFiles({ prefix: sourcePrefix });
  for (const file of sourceBlobs) {
    outcome.blobsDeleted++;
    outcome.bytesReclaimed += Number(file.metadata.size ?? 0);
  }

  if (options.dryRun) return;

  // Storage first, then Firestore. If this is interrupted between the two, what remains is an
  // orphaned document subtree whose blobs are gone — recoverable, and the next run's verification
  // still passes because the destination is untouched. The reverse order would leave objects with
  // no record naming them, which is exactly the orphan class storageSweep exists to chase.
  await bucket.deleteFiles({ prefix: sourcePrefix });
  await adminDb.recursiveDelete(sourceDoc);
}
