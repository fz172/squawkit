import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import { ENTITY_SEGMENT_THING } from "../config/entitySegment.js";
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
  thingShareDocPath,
  shareInviteDocPath,
  SHARE_ROLE,
  type ThingShareDoc,
  type ShareRole,
} from "./sharingModels.js";
import {
  isEntitledToAttachments,
  subscriptionDocPath,
} from "../subscription/entitlementModel.js";

type CreateRequest = { thingId: string; role: ShareRole; thingLabel: string };
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
export const createThingShareInvite = onCall<CreateRequest, Promise<CreateResponse>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request): Promise<CreateResponse> => {
    const { uid } = requireAuthenticatedApp(request);
    const { thingId, role, thingLabel } = parseRequest(request.data);

    // Only the aircraft's owner may invite to it. The aircraft must exist in the CALLER's tree —
    // and since the ACL is namespaced under the caller, an aircraft planted in their own tree only
    // ever mints invites to their own aircraft. Nothing to hijack.
    //
    // MIGRATION (Checkpoint 2, thing_migration_design.md §2.7a / task B9a): THIS IS THE FLIP. A
    // callable is deployed globally and called by one export name, so this is a hard cutover rather
    // than a dual deploy. DO NOT MERGE BEFORE D3 — merging to main auto-deploys (§2.7b), and
    // flipping early makes this check fail for every un-migrated account.
    const aircraft = await adminDb.doc(`users/${uid}/${ENTITY_SEGMENT_THING}/${thingId}`).get();
    if (!aircraft.exists || aircraft.data()?.deleted === true) {
      throw new HttpsError("not-found", "Aircraft not found.");
    }

    const shareRef = adminDb.doc(thingShareDocPath(uid, thingId));
    const code = generateInviteCode();
    const codeId = inviteCodeId(code);
    const now = Date.now();
    const expiresAt = Timestamp.fromMillis(now + INVITE_TTL_MS);

    await adminDb.runTransaction(async (tx) => {
      // Both reads first — a transaction must read before it writes. The subscription read only
      // matters on the bootstrap path below, but reading it unconditionally keeps the ordering simple.
      const [shareSnap, subSnap] = await Promise.all([
        tx.get(shareRef),
        tx.get(adminDb.doc(subscriptionDocPath(uid))),
      ]);

      // Non-owners cannot invite. On a not-yet-shared aircraft there is no ACL, and the caller owns
      // the aircraft (checked above) — so this bootstraps it with them as owner.
      if (shareSnap.exists) {
        const share = shareSnap.data() as ThingShareDoc;
        if (share.memberRoles[uid] !== SHARE_ROLE.OWNER) {
          throw new HttpsError("permission-denied", "Only owners can invite to this aircraft.");
        }
      } else {
        tx.set(shareRef, {
          hostUid: uid,
          thingId,
          memberRoles: { [uid]: SHARE_ROLE.OWNER },
          createdAt: FieldValue.serverTimestamp(),
          // Stamp the host's entitlement at creation so a free host's first share is gated from the
          // outset (design §9.7). Without this the field would be absent — which the broker treats as
          // enabled — until the host's next subscription write, letting a free host briefly host
          // attachments. The projector (projectAttachmentEntitlement) maintains it thereafter.
          attachmentsEnabled: isEntitledToAttachments(subSnap.data(), now),
        });
      }

      // The code doc — the only thing that can be redeemed, and unreadable by any client.
      tx.set(adminDb.doc(inviteCodeDocPath(code)), {
        hostUid: uid,
        thingId,
        role,
        createdBy: uid,
        createdAt: FieldValue.serverTimestamp(),
        expiresAt,
        // Shown to the invitee before they accept (#201). The server cannot read these out of the
        // aircraft record — it is opaque proto bytes — so they are carried here.
        thingLabel,
        hostName: request.auth?.token?.name ?? "",
        codeId, // lets cancel find this doc by a single equality filter — see inviteCodes.ts
      });

      // Owner-visible record: enough to list and cancel a pending invite, with the code itself
      // absent. Reading the invite list yields nothing redeemable.
      tx.set(adminDb.doc(shareInviteDocPath(uid, thingId, codeId)), {
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
  const thingId = typeof obj.thingId === "string" ? obj.thingId.trim() : "";
  const role = typeof obj.role === "string" ? obj.role : "";
  // Display only, and the owner is describing their own aircraft. Capped so a rogue client cannot
  // stuff the doc; empty is fine — the sheet says less rather than lying.
  const thingLabel = (typeof obj.thingLabel === "string" ? obj.thingLabel : "").slice(0, 120);
  if (thingId.length === 0) {
    throw new HttpsError("invalid-argument", "thingId is required.");
  }
  if (role !== SHARE_ROLE.OWNER && role !== SHARE_ROLE.TECHNICIAN) {
    throw new HttpsError("invalid-argument", "role must be owner or technician.");
  }
  return { thingId, role: role as ShareRole, thingLabel };
}
