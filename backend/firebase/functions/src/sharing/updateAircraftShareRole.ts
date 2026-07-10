import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { sharedAircraftRefWireDoc } from "./sharedAircraftRefWire.js";
import {
  aircraftShareDocPath,
  shareMemberDocPath,
  SHARE_ROLE,
  type AircraftShareDoc,
  type ShareRole,
} from "./sharingModels.js";

type UpdateRoleRequest = { aircraftId: string; memberUid: string; role: ShareRole };

/**
 * Changes a member's role. Owner-only. Updates the ACL, the member doc, and rewrites the member's
 * ref payload so the advisory role stays consistent. The hosting owner's role is immutable. See §3.3.
 */
export const updateAircraftShareRole = onCall<UpdateRoleRequest, Promise<{ ok: true }>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request) => {
    const { uid } = requireAuthenticatedApp(request);
    const { aircraftId, memberUid, role } = parseRequest(request.data);
    const shareRef = adminDb.doc(aircraftShareDocPath(aircraftId));

    await adminDb.runTransaction(async (tx) => {
      const snap = await tx.get(shareRef);
      if (!snap.exists) throw new HttpsError("not-found", "Share not found.");
      const share = snap.data() as AircraftShareDoc;

      if (share.memberRoles[uid] !== SHARE_ROLE.OWNER) {
        throw new HttpsError("permission-denied", "Only owners can change roles.");
      }
      if (memberUid === share.hostUid) {
        throw new HttpsError("failed-precondition", "The hosting owner's role cannot be changed.");
      }
      if (share.memberRoles[memberUid] == null) {
        throw new HttpsError("not-found", "That member is not part of this share.");
      }

      tx.update(shareRef, { [`memberRoles.${memberUid}`]: role });
      tx.update(adminDb.doc(shareMemberDocPath(aircraftId, memberUid)), { role });
      tx.set(
        adminDb.doc(`users/${memberUid}/shared_aircraft_ref/${aircraftId}`),
        sharedAircraftRefWireDoc(aircraftId, share.hostUid, role),
      );
    });

    return { ok: true };
  },
);

function parseRequest(data: unknown): UpdateRoleRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const aircraftId = typeof obj.aircraftId === "string" ? obj.aircraftId.trim() : "";
  const memberUid = typeof obj.memberUid === "string" ? obj.memberUid.trim() : "";
  const role = obj.role;
  if (aircraftId.length === 0 || memberUid.length === 0) {
    throw new HttpsError("invalid-argument", "updateAircraftShareRole requires aircraftId and memberUid.");
  }
  if (role !== SHARE_ROLE.OWNER && role !== SHARE_ROLE.TECHNICIAN) {
    throw new HttpsError("invalid-argument", "role must be 'owner' or 'technician'.");
  }
  return { aircraftId, memberUid, role };
}
