import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { INVITE_CODES_COLLECTION } from "./inviteCodes.js";
import {
  aircraftShareDocPath,
  shareInviteDocPath,
  SHARE_ROLE,
  type AircraftShareDoc,
} from "./sharingModels.js";

type CancelRequest = { aircraftId: string; codeId: string };

/**
 * Cancels a pending invite (#164).
 *
 * Server-side because the code doc is in a collection no client may touch — and because cancelling
 * means *destroying the code*, not flagging it. There is no `revoked` field any more: the code is
 * gone, so a cancelled invite and a never-existed one are the same state, which is the same error
 * the PRD wants for both.
 *
 * The owner identifies the invite by `codeId` (SHA-256 of the code), which is what they can see —
 * they cannot see the code itself, and neither can anyone else.
 */
export const cancelAircraftShareInvite = onCall<CancelRequest, Promise<{ ok: true }>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<{ ok: true }> => {
    const { uid } = requireAuthenticatedApp(request);
    const { aircraftId, codeId } = parseRequest(request.data);

    const shareSnap = await adminDb.doc(aircraftShareDocPath(uid, aircraftId)).get();
    if (!shareSnap.exists) throw new HttpsError("not-found", "Share not found.");
    const share = shareSnap.data() as AircraftShareDoc;
    if (share.memberRoles[uid] !== SHARE_ROLE.OWNER) {
      throw new HttpsError("permission-denied", "Only owners can cancel invites.");
    }

    // The owner's record holds the codeId, not the code, so the live code doc has to be found by
    // matching on it. Cheap: pending invites per aircraft are a handful, and this collection is
    // otherwise unqueryable by clients.
    const codes = await adminDb
      .collection(INVITE_CODES_COLLECTION)
      .where("hostUid", "==", uid)
      .where("aircraftId", "==", aircraftId)
      .get();

    const batch = adminDb.batch();
    for (const codeDoc of codes.docs) {
      const { createHash } = await import("node:crypto");
      if (createHash("sha256").update(codeDoc.id).digest("hex") === codeId) {
        batch.delete(codeDoc.ref);
      }
    }
    batch.delete(adminDb.doc(shareInviteDocPath(uid, aircraftId, codeId)));
    await batch.commit();

    return { ok: true };
  },
);

function parseRequest(data: unknown): CancelRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const aircraftId = typeof obj.aircraftId === "string" ? obj.aircraftId.trim() : "";
  const codeId = typeof obj.codeId === "string" ? obj.codeId.trim() : "";
  if (aircraftId.length === 0 || codeId.length === 0) {
    throw new HttpsError("invalid-argument", "aircraftId and codeId are required.");
  }
  return { aircraftId, codeId };
}
