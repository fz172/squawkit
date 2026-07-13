import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { sharedAircraftRefTombstone } from "./sharedAircraftRefWire.js";
import {
  aircraftShareDocPath,
  shareMemberDocPath,
  SHARE_ROLE,
  type AircraftShareDoc,
} from "./sharingModels.js";

/**
 * `hostUid` is routing only — it names WHICH share doc to open (#204). It is not authorization: the
 * caller's rights are still checked against the ACL found at that path, so passing someone else's
 * hostUid opens a share the caller is not in, and the checks below reject them.
 */
type RevokeRequest = { hostUid: string; aircraftId: string; memberUid: string };

/**
 * Removes a member from a share. Callable by any owner, or by the member themselves (leave). The
 * hosting owner cannot be removed. Transactionally clears the ACL entry, deletes the member doc, and
 * tombstones the ex-member's ref — the tombstone is what tells their devices to purge. See §3.3.
 */
export const revokeAircraftShare = onCall<RevokeRequest, Promise<{ ok: true }>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request) => {
    const { uid } = requireAuthenticatedApp(request);
    const { hostUid, aircraftId, memberUid } = parseRequest(request.data);
    const shareRef = adminDb.doc(aircraftShareDocPath(hostUid, aircraftId));

    await adminDb.runTransaction(async (tx) => {
      const snap = await tx.get(shareRef);
      if (!snap.exists) throw new HttpsError("not-found", "Share not found.");
      const share = snap.data() as AircraftShareDoc;

      const isOwner = share.memberRoles[uid] === SHARE_ROLE.OWNER;
      const isSelf = uid === memberUid;
      if (!isOwner && !isSelf) {
        throw new HttpsError("permission-denied", "Only owners can remove other members.");
      }
      if (memberUid === share.hostUid) {
        throw new HttpsError("failed-precondition", "The hosting owner cannot be removed.");
      }
      if (share.memberRoles[memberUid] == null) return; // already not a member — no-op

      tx.update(shareRef, { [`memberRoles.${memberUid}`]: FieldValue.delete() });
      tx.delete(adminDb.doc(shareMemberDocPath(hostUid, aircraftId, memberUid)));
      tx.set(
        adminDb.doc(`users/${memberUid}/shared_aircraft_ref/${aircraftId}`),
        sharedAircraftRefTombstone(),
      );
    });

    return { ok: true };
  },
);

/**
 * Expects `{ aircraftId: string, memberUid: string }` — the shared aircraft and the member to
 * remove (memberUid === caller for the "leave" case). Both required and non-empty.
 */
function parseRequest(data: unknown): RevokeRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const hostUid = typeof obj.hostUid === "string" ? obj.hostUid.trim() : "";
  const aircraftId = typeof obj.aircraftId === "string" ? obj.aircraftId.trim() : "";
  const memberUid = typeof obj.memberUid === "string" ? obj.memberUid.trim() : "";
  if (hostUid.length === 0) {
    throw new HttpsError("invalid-argument", "hostUid is required.");
  }
  if (aircraftId.length === 0 || memberUid.length === 0) {
    throw new HttpsError("invalid-argument", "revokeAircraftShare requires aircraftId and memberUid.");
  }
  return { hostUid, aircraftId, memberUid };
}
