import { createHash } from "node:crypto";

import type { Bucket } from "@google-cloud/storage";

import { ENTITY_SEGMENT_LEGACY, ENTITY_SEGMENT_THING } from "../config/entitySegment.js";
import { adminDb, adminStorage } from "../config/firebaseAdmin.js";
import type { SyncDocWire } from "../shared/syncDocWire.js";
import { backfillThing, rewriteRecordStoragePaths } from "./thingPayloads.js";

/**
 * The Aircraft → Thing cutover (docs/product/thing_migration_design.md §5.1, tasks B1–B3).
 *
 * One developer-managed **global batch**, not per-account work spread over weeks: every uid comes
 * straight from the `users` collection, each is processed inside its own `try`/`catch`, and the run
 * ends with a report naming which uids succeeded and which failed. The operator re-runs — the whole
 * list, or just the failures via `onlyUids` — until a run reports zero failures. That report, not
 * anyone's judgement, is the gate on Phase E and Phase G (§8 decision #4).
 *
 * **Copy only. Nothing here deletes.** The old Firestore documents and Storage objects stay exactly
 * where they are; reclaiming them is a separate pass after the 7-day grace window (§7, task B4).
 * That is what makes a failed or half-finished run safe to simply run again.
 *
 * Idempotent throughout, because re-running is the expected recovery path rather than an edge case:
 * documents are written by id (a second copy overwrites with identical bytes), the payload
 * transforms no-op on already-migrated payloads, and blobs are skipped when a verified-identical
 * object already exists at the destination.
 */

export type CutoverOptions = {
  /** Read, verify, and count — write nothing. Task B2. */
  dryRun: boolean;
  /** Restrict the run to these uids. Empty/absent means every account. Task B3. */
  onlyUids?: readonly string[];
  /**
   * Skip the per-object checksum on blob copies, verifying size only. Checksums read every byte
   * back; on a large account that is the slow part. Off by default — attachments are the data with
   * no second copy anywhere, so they get the stronger check unless someone opts out deliberately.
   */
  skipChecksum?: boolean;
  /**
   * Cloud Storage bucket to read and write. Omit to use the Admin app's default.
   *
   * Running as a plain script, there usually IS no default: `adminStorage.bucket()` reads the app's
   * `storageBucket` option, which the Cloud Functions runtime (and `firebase emulators:exec`)
   * populate from `FIREBASE_CONFIG` but a local process authenticated with ADC does not. That is
   * why this is an explicit option rather than something inferred — and why [preflight] resolves it
   * once, up front, instead of letting every account fail the same way.
   */
  bucketName?: string;
};

export type UidOutcome = {
  uid: string;
  ok: boolean;
  /** Present only when `ok` is false. */
  error?: string;
  thingsCopied: number;
  recordsCopied: number;
  blobsCopied: number;
  blobsAlreadyPresent: number;
  blobBytesCopied: number;
  /** Payloads whose embedded `Attachment.storage_path` was rewritten (§2.6). */
  storagePathsRewritten: number;
  /** Thing payloads given `template_id`/`name`/`spec`/`components` (§4.2). */
  thingsBackfilled: number;
};

export type CutoverReport = {
  dryRun: boolean;
  startedAtMs: number;
  finishedAtMs: number;
  succeeded: string[];
  /** The retry list: feed these straight back in as `onlyUids`. */
  failed: { uid: string; error: string }[];
  outcomes: UidOutcome[];
  totals: Omit<UidOutcome, "uid" | "ok" | "error">;
};

/** Firestore caps a WriteBatch at 500 operations; 400 leaves headroom, matching onAircraftDeleted. */
const BATCH_LIMIT = 400;

/**
 * Resolve the bucket once and prove it is reachable, before any account is touched.
 *
 * A misconfigured bucket is a property of the RUN, not of an account, so it must surface as one
 * error at the top rather than as N identical per-account failures in a report the operator then
 * has to read past. (It first showed up exactly that way: a production dry run reported 13 accounts
 * "failed" with the same bucket error, which read like a data problem and was not.)
 */
export async function preflight(options: CutoverOptions): Promise<Bucket> {
  let bucket: Bucket;
  try {
    bucket =
      options.bucketName != null && options.bucketName.length > 0
        ? adminStorage.bucket(options.bucketName)
        : adminStorage.bucket();
  } catch (e) {
    throw new Error(
      `Could not resolve a Cloud Storage bucket: ${String(e)}\n` +
        "Pass --bucket <name>, or set FIREBASE_STORAGE_BUCKET. A local script has no default: " +
        "adminStorage.bucket() reads the app's storageBucket option, which only the Cloud Functions " +
        "runtime and firebase emulators:exec populate.",
    );
  }

  // A one-object LIST rather than `bucket.exists()`: listing is precisely what the copy does, so
  // this proves the capability that matters instead of a weaker adjacent one. It is also the only
  // probe that works against the Storage emulator, whose auto-created bucket reports exists() ===
  // false.
  try {
    await bucket.getFiles({ maxResults: 1 });
  } catch (e) {
    throw new Error(
      `Cloud Storage bucket "${bucket.name}" is not readable with these credentials: ${String(e)}\n` +
        "Check --bucket (note: projects created before the naming change use " +
        "<project>.appspot.com rather than <project>.firebasestorage.app) and the account behind " +
        "GOOGLE_APPLICATION_CREDENTIALS.",
    );
  }
  return bucket;
}

function emptyOutcome(uid: string): UidOutcome {
  return {
    uid,
    ok: true,
    thingsCopied: 0,
    recordsCopied: 0,
    blobsCopied: 0,
    blobsAlreadyPresent: 0,
    blobBytesCopied: 0,
    storagePathsRewritten: 0,
    thingsBackfilled: 0,
  };
}

/**
 * Every account, straight from Firestore rather than a hand-kept list.
 *
 * `listDocuments()` rather than `get()` on purpose: it returns references for documents that exist
 * only as parents of a subcollection. A `users/{uid}` doc with no fields of its own still owns an
 * `/aircraft` subtree, and `get()` would not see it.
 */
async function allUids(): Promise<string[]> {
  const refs = await adminDb.collection("users").listDocuments();
  return refs.map((ref) => ref.id);
}

export async function runThingCutover(options: CutoverOptions): Promise<CutoverReport> {
  const startedAtMs = Date.now();
  const uids =
    options.onlyUids != null && options.onlyUids.length > 0
      ? [...options.onlyUids]
      : await allUids();

  const bucket = await preflight(options);

  const outcomes: UidOutcome[] = [];
  for (const uid of uids) {
    // Built here, and mutated in place by migrateUid, so that a failure keeps whatever the account
    // had already accomplished. Discarding it (the obvious `catch` that returns a fresh empty
    // outcome) makes the report actively misleading — it showed "0 things copied" for accounts that
    // had in fact copied their Thing document before failing on the blob step.
    const outcome = emptyOutcome(uid);
    try {
      await migrateUid(uid, bucket, options, outcome);
    } catch (e) {
      // Per-uid isolation is the whole shape of this script (§5.1): one account's failure must not
      // abandon the rest of the batch, and the report is what turns it into a retry list.
      outcome.ok = false;
      outcome.error = String(e);
    }
    outcomes.push(outcome);
  }

  const totals = outcomes.reduce(
    (acc, o) => ({
      thingsCopied: acc.thingsCopied + o.thingsCopied,
      recordsCopied: acc.recordsCopied + o.recordsCopied,
      blobsCopied: acc.blobsCopied + o.blobsCopied,
      blobsAlreadyPresent: acc.blobsAlreadyPresent + o.blobsAlreadyPresent,
      blobBytesCopied: acc.blobBytesCopied + o.blobBytesCopied,
      storagePathsRewritten: acc.storagePathsRewritten + o.storagePathsRewritten,
      thingsBackfilled: acc.thingsBackfilled + o.thingsBackfilled,
    }),
    {
      thingsCopied: 0,
      recordsCopied: 0,
      blobsCopied: 0,
      blobsAlreadyPresent: 0,
      blobBytesCopied: 0,
      storagePathsRewritten: 0,
      thingsBackfilled: 0,
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
    outcomes,
    totals,
  };
}

/**
 * One account, end to end.
 *
 * Shared aircraft live in the HOST's tree (`AircraftScopeResolverImpl`), so iterating owners covers
 * every aircraft exactly once — a member's uid simply has no `/aircraft` subtree of its own to
 * migrate, and their access is re-established by the ACL, which Phase G moves separately.
 */
async function migrateUid(
  uid: string,
  bucket: Bucket,
  options: CutoverOptions,
  outcome: UidOutcome,
): Promise<void> {
  const aircraftRefs = await adminDb
    .collection(`users/${uid}/${ENTITY_SEGMENT_LEGACY}`)
    .listDocuments();

  for (const aircraftRef of aircraftRefs) {
    const acId = aircraftRef.id;
    await copyThingDoc(uid, acId, aircraftRef, options, outcome);
    await copyChildRecords(uid, acId, aircraftRef, options, outcome);
    await copyBlobs(uid, acId, bucket, options, outcome);
  }

  await verify(uid, aircraftRefs.map((r) => r.id), bucket, options);
}

/** The Thing document itself: envelope schema rewritten, payload backfilled. */
async function copyThingDoc(
  uid: string,
  acId: string,
  source: FirebaseFirestore.DocumentReference,
  options: CutoverOptions,
  outcome: UidOutcome,
): Promise<void> {
  const snap = await source.get();
  if (!snap.exists) return; // a parent-only doc: it owns subcollections but has no fields itself

  const doc = snap.data() as SyncDocWire;
  const { value, changed } = backfillThing(doc);
  outcome.thingsCopied++;
  if (changed > 0) outcome.thingsBackfilled++;

  if (!options.dryRun) {
    await adminDb.doc(`users/${uid}/${ENTITY_SEGMENT_THING}/${acId}`).set(value);
  }
}

/**
 * Every child record, under every subcollection.
 *
 * Subcollections are discovered with `listCollections()` rather than a hardcoded kind list — the
 * same reasoning `onAircraftDeleted.tombstoneChildren` gives: it stays correct as per-aircraft kinds
 * change, with nothing to keep in sync with the client's `CollectionKind`. A hardcoded list that
 * missed a kind would silently leave that data behind on the old path.
 */
async function copyChildRecords(
  uid: string,
  acId: string,
  source: FirebaseFirestore.DocumentReference,
  options: CutoverOptions,
  outcome: UidOutcome,
): Promise<void> {
  for (const collection of await source.listCollections()) {
    const snap = await collection.get();
    let batch = adminDb.batch();
    let pending = 0;

    for (const record of snap.docs) {
      const { value, changed } = rewriteRecordStoragePaths(record.data() as SyncDocWire);
      outcome.recordsCopied++;
      outcome.storagePathsRewritten += changed;

      if (options.dryRun) continue;

      batch.set(
        adminDb.doc(
          `users/${uid}/${ENTITY_SEGMENT_THING}/${acId}/${collection.id}/${record.id}`,
        ),
        value,
      );
      if (++pending >= BATCH_LIMIT) {
        await batch.commit();
        batch = adminDb.batch();
        pending = 0;
      }
    }
    if (pending > 0) await batch.commit();
  }
}

/**
 * Copy every blob object, verifying each one before counting it copied.
 *
 * Attachments are the only data in this migration with no second copy anywhere — a lost proto
 * payload could in principle be reconstructed from a device, a lost photo cannot — so this is the
 * step that verifies rather than trusts. Nothing is deleted here either way (§7).
 */
async function copyBlobs(
  uid: string,
  acId: string,
  bucket: Bucket,
  options: CutoverOptions,
  outcome: UidOutcome,
): Promise<void> {
  const sourcePrefix = `users/${uid}/${ENTITY_SEGMENT_LEGACY}/${acId}/blobs/`;
  const [files] = await bucket.getFiles({ prefix: sourcePrefix });

  for (const file of files) {
    const blobId = file.name.slice(sourcePrefix.length);
    if (blobId.length === 0) continue; // the prefix placeholder, not an object
    const destPath = `users/${uid}/${ENTITY_SEGMENT_THING}/${acId}/blobs/${blobId}`;
    const dest = bucket.file(destPath);

    const sourceBytes = await file.download();
    const sourceBuffer = sourceBytes[0];

    const [destExists] = await dest.exists();
    if (destExists) {
      // A re-run, or a partially completed previous pass. Verify rather than assume: an object that
      // is present but different is worse than one that is missing, because nothing else will
      // notice it.
      const [existing] = await dest.download();
      if (identical(existing, sourceBuffer, options)) {
        outcome.blobsAlreadyPresent++;
        continue;
      }
    }

    if (options.dryRun) {
      outcome.blobsCopied++;
      outcome.blobBytesCopied += sourceBuffer.byteLength;
      continue;
    }

    await file.copy(dest);

    const [copied] = await dest.download();
    if (!identical(copied, sourceBuffer, options)) {
      throw new Error(
        `Blob verification failed for ${destPath}: ` +
          `${copied.byteLength} bytes at destination vs ${sourceBuffer.byteLength} at source`,
      );
    }
    outcome.blobsCopied++;
    outcome.blobBytesCopied += sourceBuffer.byteLength;
  }
}

function identical(a: Buffer, b: Buffer, options: CutoverOptions): boolean {
  if (a.byteLength !== b.byteLength) return false;
  if (options.skipChecksum === true) return true;
  return sha256(a) === sha256(b);
}

function sha256(buffer: Buffer): string {
  return createHash("sha256").update(buffer).digest("hex");
}

/**
 * Confirm the destination holds as much as the source, per aircraft.
 *
 * Deliberately an inequality, not an equality: the destination may legitimately hold MORE (a re-run
 * after the source was partially cleaned up, or a document written directly at `/thing/`). Fewer
 * documents at the destination is the only outcome that means data did not make it, and that is
 * what this refuses to pass.
 *
 * Skipped on a dry run, which by definition wrote nothing.
 */
async function verify(
  uid: string,
  acIds: readonly string[],
  bucket: Bucket,
  options: CutoverOptions,
): Promise<void> {
  if (options.dryRun) return;

  for (const acId of acIds) {
    const sourceDoc = adminDb.doc(`users/${uid}/${ENTITY_SEGMENT_LEGACY}/${acId}`);
    const destDoc = adminDb.doc(`users/${uid}/${ENTITY_SEGMENT_THING}/${acId}`);

    for (const collection of await sourceDoc.listCollections()) {
      const sourceCount = (await collection.count().get()).data().count;
      const destCount = (
        await destDoc.collection(collection.id).count().get()
      ).data().count;
      if (destCount < sourceCount) {
        throw new Error(
          `Verification failed for users/${uid}/.../${acId}/${collection.id}: ` +
            `${destCount} documents copied, ${sourceCount} at source`,
        );
      }
    }

    const [sourceBlobs] = await bucket.getFiles({
      prefix: `users/${uid}/${ENTITY_SEGMENT_LEGACY}/${acId}/blobs/`,
    });
    const [destBlobs] = await bucket.getFiles({
      prefix: `users/${uid}/${ENTITY_SEGMENT_THING}/${acId}/blobs/`,
    });
    if (destBlobs.length < sourceBlobs.length) {
      throw new Error(
        `Blob verification failed for users/${uid}/.../${acId}: ` +
          `${destBlobs.length} objects copied, ${sourceBlobs.length} at source`,
      );
    }
  }
}
