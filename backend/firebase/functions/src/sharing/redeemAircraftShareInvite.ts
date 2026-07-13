import { createHash } from "node:crypto";

import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { sharedAircraftRefWireDoc } from "./sharedAircraftRefWire.js";
import {
  aircraftShareDocPath,
  shareInviteDocPath,
  shareMemberDocPath,
  type AircraftShareDoc,
  type ShareInviteDoc,
  type ShareRole,
} from "./sharingModels.js";

/**
 * `hostUid` is routing only — it names WHICH share doc to open (#204). It is not authorization: the
 * caller's rights are still checked against the ACL found at that path, so passing someone else's
 * hostUid opens a share the caller is not in, and the checks below reject them.
 */
type RedeemRequest = { hostUid: string; aircraftId: string; secret: string };
type RedeemResponse = {
  aircraftId: string;
  hostUid: string;
  role: ShareRole;
  alreadyMember: boolean;
};

/**
 * Redeems a capability invite. Must write into two account trees (the share's member list and the
 * redeemer's own shared_aircraft_ref) and enforce invite validity atomically — hence a callable
 * with the Admin SDK, not client writes + rules. See docs/sharing §3.2.
 */
export const redeemAircraftShareInvite = onCall<RedeemRequest, Promise<RedeemResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<RedeemResponse> => {
    const { uid } = requireAuthenticatedApp(request);
    requireNonAnonymous(request);
    const { hostUid, aircraftId, secret } = parseRequest(request.data);
    const tokenHash = sha256Hex(secret);

    const shareRef = adminDb.doc(aircraftShareDocPath(hostUid, aircraftId));
    const inviteRef = adminDb.doc(shareInviteDocPath(hostUid, aircraftId, tokenHash));

    return adminDb.runTransaction(async (tx): Promise<RedeemResponse> => {
      const [shareSnap, inviteSnap] = await Promise.all([tx.get(shareRef), tx.get(inviteRef)]);
      if (!shareSnap.exists || !inviteSnap.exists) {
        throw new HttpsError("not-found", "This invite is no longer valid.");
      }
      const share = shareSnap.data() as AircraftShareDoc;
      const invite = inviteSnap.data() as ShareInviteDoc;

      // `revoked` is set by owner action (cancelInvite) — a manual cancellation, distinct from the
      // time-based `expiresAt` below. See docs/sharing §3.1/§3.3.
      if (invite.revoked) throw new HttpsError("failed-precondition", "This invite was cancelled.");
      if (invite.expiresAt.toMillis() <= Date.now()) {
        throw new HttpsError("failed-precondition", "This invite has expired.");
      }
      if (invite.useCount >= invite.maxUses) {
        throw new HttpsError("failed-precondition", "This invite has already been used.");
      }

      // Already a member → friendly no-op; the token is NOT consumed.
      const existingRole = share.memberRoles[uid];
      if (existingRole != null) {
        return { aircraftId, hostUid: share.hostUid, role: existingRole, alreadyMember: true };
      }

      const role = invite.role;
      tx.update(inviteRef, { useCount: invite.useCount + 1 });
      tx.update(shareRef, { [`memberRoles.${uid}`]: role });
      tx.set(adminDb.doc(shareMemberDocPath(hostUid, aircraftId, uid)), {
        role,
        displayName: request.auth?.token?.name ?? "",
        photoUrl: request.auth?.token?.picture ?? null,
        technicianMirror: null,
        addedAt: FieldValue.serverTimestamp(),
        invitedBy: invite.createdBy,
      });
      tx.set(
        adminDb.doc(`users/${uid}/shared_aircraft_ref/${aircraftId}`),
        sharedAircraftRefWireDoc(aircraftId, share.hostUid, role),
      );
      return { aircraftId, hostUid: share.hostUid, role, alreadyMember: false };
    });
  },
);

function requireNonAnonymous(request: CallableRequest<unknown>): void {
  if (request.auth?.token?.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("permission-denied", "Guests must sign in before joining a shared aircraft.");
  }
}

/** tokenHash = SHA-256 of the secret string carried in the invite URL fragment (hex). */
function sha256Hex(secret: string): string {
  return createHash("sha256").update(secret).digest("hex");
}

/**
 * Expects `{ aircraftId: string, secret: string }` — the aircraft id and the invite secret parsed
 * from the share URL fragment (`/share#{aircraftId}.{secret}`). Both required and non-empty.
 */
function parseRequest(data: unknown): RedeemRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const hostUid = typeof obj.hostUid === "string" ? obj.hostUid.trim() : "";
  const aircraftId = typeof obj.aircraftId === "string" ? obj.aircraftId.trim() : "";
  const secret = typeof obj.secret === "string" ? obj.secret : "";
  if (hostUid.length === 0) {
    throw new HttpsError("invalid-argument", "hostUid is required.");
  }
  if (aircraftId.length === 0 || secret.length === 0) {
    throw new HttpsError("invalid-argument", "redeemAircraftShareInvite requires aircraftId and secret.");
  }
  return { hostUid, aircraftId, secret };
}
