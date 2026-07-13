import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import {
  formatInviteCode,
  generateInviteCode,
  inviteCodeDocPath,
  inviteCodeId,
  INVITE_TTL_MS,
} from "./inviteCodes.js";
import {
  aircraftShareDocPath,
  shareInviteDocPath,
  SHARE_ROLE,
  type AircraftShareDoc,
  type ShareRole,
} from "./sharingModels.js";

type CreateRequest = { aircraftId: string; role: ShareRole; aircraftLabel: string };
type CreateResponse = { code: string; formattedCode: string; codeId: string; expiresAtMs: number };

/**
 * Mints a pairing-code invite (#164).
 *
 * Server-side because the code doc lives in a collection **no client may touch** — that is the whole
 * point: the invitee holds an opaque code instead of an aircraft id, so there is nothing to
 * fabricate a same-id aircraft against (#202/#204).
 *
 * It also bootstraps the ACL, which used to be a client write. The caller can only ever create a
 * share in their own namespace (rules pin `{hostUid}` to the token), and here that is structural:
 * `hostUid` IS the caller.
 */
export const createAircraftShareInvite = onCall<CreateRequest, Promise<CreateResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<CreateResponse> => {
    const { uid } = requireAuthenticatedApp(request);
    const { aircraftId, role, aircraftLabel } = parseRequest(request.data);

    // Only the aircraft's owner may invite to it. The aircraft must exist in the CALLER's tree —
    // and since the ACL is namespaced under the caller, an aircraft planted in their own tree only
    // ever mints invites to their own aircraft. Nothing to hijack.
    const aircraft = await adminDb.doc(`users/${uid}/aircraft/${aircraftId}`).get();
    if (!aircraft.exists || aircraft.data()?.deleted === true) {
      throw new HttpsError("not-found", "Aircraft not found.");
    }

    const shareRef = adminDb.doc(aircraftShareDocPath(uid, aircraftId));
    const code = generateInviteCode();
    const codeId = inviteCodeId(code);
    const now = Date.now();
    const expiresAt = Timestamp.fromMillis(now + INVITE_TTL_MS);

    await adminDb.runTransaction(async (tx) => {
      const shareSnap = await tx.get(shareRef);

      // Non-owners cannot invite. On a not-yet-shared aircraft there is no ACL, and the caller owns
      // the aircraft (checked above) — so this bootstraps it with them as owner.
      if (shareSnap.exists) {
        const share = shareSnap.data() as AircraftShareDoc;
        if (share.memberRoles[uid] !== SHARE_ROLE.OWNER) {
          throw new HttpsError("permission-denied", "Only owners can invite to this aircraft.");
        }
      } else {
        tx.set(shareRef, {
          hostUid: uid,
          aircraftId,
          memberRoles: { [uid]: SHARE_ROLE.OWNER },
          createdAt: FieldValue.serverTimestamp(),
        });
      }

      // The code doc — the only thing that can be redeemed, and unreadable by any client.
      tx.set(adminDb.doc(inviteCodeDocPath(code)), {
        hostUid: uid,
        aircraftId,
        role,
        createdBy: uid,
        createdAt: FieldValue.serverTimestamp(),
        expiresAt,
        // Shown to the invitee before they accept (#201). The server cannot read these out of the
        // aircraft record — it is opaque proto bytes — so they are carried here.
        aircraftLabel,
        hostName: request.auth?.token?.name ?? "",
      });

      // Owner-visible record: enough to list and cancel a pending invite, with the code itself
      // absent. Reading the invite list yields nothing redeemable.
      tx.set(adminDb.doc(shareInviteDocPath(uid, aircraftId, codeId)), {
        role,
        createdBy: uid,
        createdAt: FieldValue.serverTimestamp(),
        expiresAt,
      });
    });

    // The code is returned exactly once, here. It is never stored anywhere a client can read it.
    return { code, formattedCode: formatInviteCode(code), codeId, expiresAtMs: expiresAt.toMillis() };
  },
);

function parseRequest(data: unknown): CreateRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const aircraftId = typeof obj.aircraftId === "string" ? obj.aircraftId.trim() : "";
  const role = typeof obj.role === "string" ? obj.role : "";
  // Display only, and the owner is describing their own aircraft. Capped so a rogue client cannot
  // stuff the doc; empty is fine — the sheet says less rather than lying.
  const aircraftLabel = (typeof obj.aircraftLabel === "string" ? obj.aircraftLabel : "").slice(0, 120);
  if (aircraftId.length === 0) {
    throw new HttpsError("invalid-argument", "aircraftId is required.");
  }
  if (role !== SHARE_ROLE.OWNER && role !== SHARE_ROLE.TECHNICIAN) {
    throw new HttpsError("invalid-argument", "role must be owner or technician.");
  }
  return { aircraftId, role: role as ShareRole, aircraftLabel };
}
