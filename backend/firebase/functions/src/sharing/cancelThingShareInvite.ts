import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { INVITE_CODES_COLLECTION, type InviteCodeDoc } from "./inviteCodes.js";
import {
  thingShareDocPath,
  shareInviteDocPath,
  SHARE_ROLE,
  type ThingShareDoc,
} from "./sharingModels.js";

type CancelRequest = { thingId: string; codeId: string };

/**
 * Cancels a pending invite (#164).
 *
 * Server-side because the code doc is in a collection no client may touch — and because cancelling
 * means *destroying* the code, not flagging it. There is no `revoked` field any more: the code is
 * gone, so a cancelled invite and a never-existed one are the same state, which is the same error
 * the PRD wants for both.
 *
 * The owner identifies the invite by `codeId` (SHA-256 of the code) — what they can see. They cannot
 * see the code itself, and neither can anyone else.
 */
export const cancelThingShareInvite = onCall<CancelRequest, Promise<{ ok: true }>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<{ ok: true }> => {
    const { uid } = requireAuthenticatedApp(request);
    const { thingId, codeId } = parseRequest(request.data);

    const shareSnap = await adminDb.doc(thingShareDocPath(uid, thingId)).get();
    if (!shareSnap.exists) throw new HttpsError("not-found", "Share not found.");
    const share = shareSnap.data() as ThingShareDoc;
    if (share.memberRoles[uid] !== SHARE_ROLE.OWNER) {
      throw new HttpsError("permission-denied", "Only owners can cancel invites.");
    }

    // ONE equality filter, served by the automatic single-field index. A compound query
    // (hostUid + thingId) would need a composite index — and the emulator does not enforce
    // indexes, so it would have passed here and failed in production with FAILED_PRECONDITION.
    const codes = await adminDb
      .collection(INVITE_CODES_COLLECTION)
      .where("codeId", "==", codeId)
      .limit(1)
      .get();

    const batch = adminDb.batch();
    for (const codeDoc of codes.docs) {
      // A codeId is a hash, so a match is the invite — but check the owner anyway rather than trust
      // the caller's word about which invite is theirs.
      const invite = codeDoc.data() as InviteCodeDoc;
      if (invite.hostUid === uid && invite.thingId === thingId) {
        batch.delete(codeDoc.ref);
      }
    }
    batch.delete(adminDb.doc(shareInviteDocPath(uid, thingId, codeId)));
    await batch.commit();

    return { ok: true };
  },
);

function parseRequest(data: unknown): CancelRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const thingId = typeof obj.thingId === "string" ? obj.thingId.trim() : "";
  const codeId = typeof obj.codeId === "string" ? obj.codeId.trim() : "";
  if (thingId.length === 0 || codeId.length === 0) {
    throw new HttpsError("invalid-argument", "thingId and codeId are required.");
  }
  return { thingId, codeId };
}
