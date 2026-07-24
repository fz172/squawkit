import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";

import { adminDb } from "../config/firebaseAdmin.js";
import { FUNCTION_REGION } from "../config/env.js";
import {
  AIRCRAFT_SHARES_COLLECTION,
  SHARE_AIRCRAFT_SUBCOLLECTION,
} from "../sharing/sharingModels.js";
import { isEntitledToAttachments } from "./entitlementModel.js";

// Firestore caps a WriteBatch at 500 ops; 400 leaves headroom, matching onAircraftDeleted's fan-out.
const BATCH_LIMIT = 400;

/**
 * Projects the host's attachment entitlement onto their shared aircraft (design §9.7, P8.7 #248).
 *
 * The gate is a property of the aircraft — `attachmentsEnabled(acId) = entitlement(hostUid)` — so a
 * member reads a boolean on the ACL root and never the host's billing. This trigger is the single
 * writer of that boolean: when `subscriptions/{uid}` changes, it recomputes the host's effective
 * entitlement and stamps `attachmentsEnabled` on every `aircraft_shares/{uid}/aircraft/*` doc.
 *
 * Fires on EVERY write to the doc, including the storage sweep's `storageBytesUsed` write, so it
 * exits early when the entitlement is unchanged — both sides evaluated at the same `now`, so a write
 * that leaves the entitlement fields alone (the sweep) is a guaranteed no-op, not a needless fan-out.
 *
 * Known gap (v1): resolution is time-dependent (a CANCELED subscription lapses at its period end),
 * but a trigger only fires on a WRITE. A subscription that lapses purely by the clock passing, with
 * no event written, will not re-project until the next write. The v1 sources — admin promo grants and
 * the stub — always write on a state change, and a real billing provider issues an expiry event; a
 * scheduled reconciler can close the clock-only case if one is ever needed.
 */
export const projectAttachmentEntitlement = onDocumentWritten(
  { document: "subscriptions/{uid}", region: FUNCTION_REGION },
  async (event) => {
    const { uid } = event.params;
    // One clock reading for both sides: a write that doesn't touch the entitlement fields resolves
    // identically before and after and is skipped, whatever the wall time.
    const now = Date.now();
    const enabledBefore = isEntitledToAttachments(event.data?.before?.data(), now);
    const enabledAfter = isEntitledToAttachments(event.data?.after?.data(), now);
    if (enabledBefore === enabledAfter) return;

    await projectAttachmentsEnabled(uid, enabledAfter);
  },
);

/**
 * Set `attachmentsEnabled = [enabled]` on all of [hostUid]'s shared-aircraft ACL roots. Docs already
 * at the target value are skipped so a re-run writes nothing, and it is safe to call from anywhere
 * that needs to reconcile a host's shares (the trigger, or a future backfill).
 */
export async function projectAttachmentsEnabled(
  hostUid: string,
  enabled: boolean,
): Promise<void> {
  const shares = await adminDb
    .collection(`${AIRCRAFT_SHARES_COLLECTION}/${hostUid}/${SHARE_AIRCRAFT_SUBCOLLECTION}`)
    .get();

  let batch = adminDb.batch();
  let pending = 0;
  let updated = 0;
  for (const doc of shares.docs) {
    if (doc.data()?.attachmentsEnabled === enabled) continue;
    batch.update(doc.ref, { attachmentsEnabled: enabled });
    updated++;
    if (++pending >= BATCH_LIMIT) {
      await batch.commit();
      batch = adminDb.batch();
      pending = 0;
    }
  }
  if (pending > 0) await batch.commit();

  logger.info("Projected attachment entitlement", { hostUid, enabled, updated });
}
