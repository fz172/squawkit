import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { inviteCodeDocPath, inviteCodeId, normalizeInviteCode } from "./inviteCodes.js";
import { recordFailedAttempt, requireAttemptsRemaining } from "./rateLimit.js";
import { sharedAircraftRefWireDoc } from "./sharedAircraftRefWire.js";
import {
  aircraftShareDocPath,
  shareInviteDocPath,
  shareMemberDocPath,
  type AircraftShareDoc,
  type InviteCodeDoc,
  type ShareRole,
} from "./sharingModels.js";

type RedeemRequest = { code: string };
type RedeemResponse = {
  aircraftId: string;
  hostUid: string;
  role: ShareRole;
  alreadyMember: boolean;
};

/**
 * Redeems a pairing code (#164).
 *
 * The caller sends **only the code** — no aircraft id, no host uid. Those live in the code doc, which
 * no client can read, so an invitee learns the aircraft only by joining it. That is what closes
 * #202/#204 at the source: a non-member never holds an aircraft id to fabricate against.
 *
 * Single-use is total: the code doc is DELETED on success. "Already used" and "never existed" then
 * collapse into one state — which is the same error the PRD wants for both (C3/C4/C5).
 */
export const redeemAircraftShareInvite = onCall<RedeemRequest, Promise<RedeemResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<RedeemResponse> => {
    const { uid } = requireAuthenticatedApp(request);
    requireNonAnonymous(request);
    const code = parseRequest(request.data);

    // Checked BEFORE dereferencing: a locked-out caller must learn nothing about whether the code
    // exists. A ~39-bit code is only safe because guessing is metered.
    await requireAttemptsRemaining(uid);

    const codeRef = adminDb.doc(inviteCodeDocPath(code));
    const codeSnap = await codeRef.get();
    if (!codeSnap.exists) {
      await recordFailedAttempt(uid);
      throw new HttpsError("not-found", "This invite is no longer valid.");
    }
    const invite = codeSnap.data() as InviteCodeDoc;
    if (invite.expiresAt.toMillis() <= Date.now()) {
      await recordFailedAttempt(uid);
      throw new HttpsError("not-found", "This invite is no longer valid.");
    }

    const { hostUid, aircraftId, role } = invite;
    const shareRef = adminDb.doc(aircraftShareDocPath(hostUid, aircraftId));

    return adminDb.runTransaction(async (tx): Promise<RedeemResponse> => {
      // Re-read the code inside the transaction: two devices racing the same code must not both win.
      const [shareSnap, freshCode] = await Promise.all([tx.get(shareRef), tx.get(codeRef)]);
      if (!freshCode.exists || !shareSnap.exists) {
        throw new HttpsError("not-found", "This invite is no longer valid.");
      }

      const share = shareSnap.data() as AircraftShareDoc;

      // Already a member → friendly no-op, and the code is NOT burned: the owner may still be waiting
      // for the person they actually meant to invite.
      const existingRole = share.memberRoles[uid];
      if (existingRole != null) {
        return { aircraftId, hostUid, role: existingRole, alreadyMember: true };
      }

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
        sharedAircraftRefWireDoc(aircraftId, hostUid, role),
      );

      // Burn it: the code doc and the owner's pending-invite record both go.
      tx.delete(codeRef);
      tx.delete(adminDb.doc(shareInviteDocPath(hostUid, aircraftId, inviteCodeId(code))));

      return { aircraftId, hostUid, role, alreadyMember: false };
    });
  },
);

function requireNonAnonymous(request: CallableRequest<unknown>): void {
  if (request.auth?.token?.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("permission-denied", "Guests must sign in before joining a shared aircraft.");
  }
}

/** Accepts what a human types: lowercase, spaces, and the displayed `EFA1-GGTH` grouping. */
function parseRequest(data: unknown): string {
  const obj = (data ?? {}) as Record<string, unknown>;
  const raw = typeof obj.code === "string" ? obj.code : "";
  const code = normalizeInviteCode(raw);
  if (code.length === 0) {
    throw new HttpsError("invalid-argument", "An invite code is required.");
  }
  return code;
}
