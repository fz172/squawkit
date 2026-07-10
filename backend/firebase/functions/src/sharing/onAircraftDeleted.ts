import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { sharedAircraftRefTombstone } from "./sharedAircraftRefWire.js";
import { aircraftShareDocPath, type AircraftShareDoc } from "./sharingModels.js";

/** The synced per-aircraft kinds nested under users/{uid}/aircraft/{acId}. */
const PER_AIRCRAFT_KINDS = [
  "maintenance_log",
  "maintenance_task",
  "maintenance_overview",
  "squawk",
] as const;

const BATCH_LIMIT = 400;

/**
 * Fires when an aircraft doc is written. On the deleted false→true transition (the host tombstones
 * the aircraft), it:
 *   1. tears down the share — tombstones every member's ref and deletes aircraft_shares/{acId} (§3.3);
 *   2. cascades the delete to the aircraft's child records so they don't orphan (#155/#157 first slice).
 *
 * The cascade runs for every aircraft, shared or not, closing the single-user orphaning gap too.
 */
export const onAircraftDeleted = onDocumentWritten(
  { document: "users/{uid}/aircraft/{acId}", region: FUNCTION_REGION },
  async (event) => {
    const after = event.data?.after;
    if (after == null || !after.exists) return; // hard-deleted doc — nothing to cascade from
    const wasDeleted = event.data?.before?.exists === true && event.data.before.data()?.deleted === true;
    const isDeleted = after.data()?.deleted === true;
    if (!isDeleted || wasDeleted) return; // act only on the false→true transition

    const { uid, acId } = event.params;

    await tearDownShare(acId);
    await tombstoneChildren(uid, acId);
  },
);

/** Tombstone every member's ref, then recursively delete the aircraft_shares tree. No-op if unshared. */
async function tearDownShare(acId: string): Promise<void> {
  const shareRef = adminDb.doc(aircraftShareDocPath(acId));
  const snap = await shareRef.get();
  if (!snap.exists) return;

  const share = snap.data() as AircraftShareDoc;
  await Promise.all(
    Object.keys(share.memberRoles)
      .filter((memberUid) => memberUid !== share.hostUid) // the host owns the data directly, no ref
      .map((memberUid) =>
        adminDb
          .doc(`users/${memberUid}/shared_aircraft_ref/${acId}`)
          .set(sharedAircraftRefTombstone()),
      ),
  );
  await adminDb.recursiveDelete(shareRef);
}

/** Tombstone every child record (soft delete) so it propagates and can be GC'd — never hard-deleted here. */
async function tombstoneChildren(uid: string, acId: string): Promise<void> {
  for (const kind of PER_AIRCRAFT_KINDS) {
    const docs = await adminDb.collection(`users/${uid}/aircraft/${acId}/${kind}`).get();
    let batch = adminDb.batch();
    let pending = 0;
    for (const child of docs.docs) {
      if (child.data()?.deleted === true) continue; // already tombstoned
      batch.update(child.ref, { deleted: true, lastUpdateTimestamp: FieldValue.serverTimestamp() });
      if (++pending >= BATCH_LIMIT) {
        await batch.commit();
        batch = adminDb.batch();
        pending = 0;
      }
    }
    if (pending > 0) await batch.commit();
  }
}
