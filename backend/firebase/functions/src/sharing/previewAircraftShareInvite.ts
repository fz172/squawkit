import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { inviteCodeDocPath, normalizeInviteCode } from "./inviteCodes.js";
import { recordFailedAttempt, requireAttemptsRemaining } from "./rateLimit.js";
import { type InviteCodeDoc, type ShareRole } from "./sharingModels.js";

type PreviewRequest = { code: string };
type PreviewResponse = {
  /** e.g. "N2037O · Cessna 172". Denormalized at creation — the aircraft record is opaque bytes. */
  aircraftLabel: string;
  hostName: string;
  role: ShareRole;
};

/**
 * Resolves a pairing code to *just enough* to decide whether to accept it — and grants nothing (#201).
 *
 * This is only possible because of #164. Previously the invitee held a real aircraft id, and the
 * rules (correctly) refuse to resolve one for a non-member — so accepting an invite meant accepting
 * blind. A code is an opaque handle the server can dereference on their behalf, which is exactly the
 * moment a user should be able to check they are not being talked into a stranger's fleet.
 *
 * Deliberately narrow: registration, make/model, the inviting owner's display name, and the offered
 * role. No ids leak — not the aircraft id, not the host uid — so a preview leaves the caller with
 * nothing they could fabricate against (#202/#204).
 *
 * Rate-limited on the SAME budget as redeem. A preview answers "is this code real?" just as well as a
 * redeem does, so leaving it unmetered would hand an attacker a free oracle against a ~39-bit code —
 * the easy mistake, because preview feels harmless.
 */
export const previewAircraftShareInvite = onCall<PreviewRequest, Promise<PreviewResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<PreviewResponse> => {
    const { uid } = requireAuthenticatedApp(request);
    const code = parseRequest(request.data);

    await requireAttemptsRemaining(uid);

    const codeSnap = await adminDb.doc(inviteCodeDocPath(code)).get();
    if (!codeSnap.exists) {
      await recordFailedAttempt(uid);
      throw new HttpsError("not-found", "This invite is no longer valid.");
    }
    const invite = codeSnap.data() as InviteCodeDoc;
    if (invite.expiresAt.toMillis() <= Date.now()) {
      await recordFailedAttempt(uid);
      throw new HttpsError("not-found", "This invite is no longer valid.");
    }

    // Note what is NOT returned: the aircraft id and the host uid. A preview must leave the caller
    // holding nothing they could fabricate a same-id aircraft against (#202/#204).
    return {
      aircraftLabel: invite.aircraftLabel ?? "",
      hostName: invite.hostName ?? "",
      role: invite.role,
    };
  },
);

function parseRequest(data: unknown): string {
  const obj = (data ?? {}) as Record<string, unknown>;
  const raw = typeof obj.code === "string" ? obj.code : "";
  const code = normalizeInviteCode(raw);
  if (code.length === 0) {
    throw new HttpsError("invalid-argument", "An invite code is required.");
  }
  return code;
}
