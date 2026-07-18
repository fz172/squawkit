import type { Timestamp } from "firebase-admin/firestore";

/**
 * Schema for the `aircraft_shares` ACL tree — the plain-field (non-entity) documents that govern
 * aircraft sharing. These live OUTSIDE the opaque SyncDocWire entity path precisely so that
 * security rules can read `member_roles` and Cloud Functions can validate invites. See
 * docs/sharing/aircraft_sharing_design.html §2.1.
 *
 * This module is the single source of truth for collection names, document paths, and field
 * shapes; the redeem/revoke/updateRole functions and the Firestore emulator rules suite both
 * consume it so paths and roles never drift. Types only — no runtime behavior.
 */

export const SHARE_ROLE = {
  OWNER: "owner",
  TECHNICIAN: "technician",
} as const;

export type ShareRole = (typeof SHARE_ROLE)[keyof typeof SHARE_ROLE];

/** Collection and subcollection names under `aircraft_shares/{hostUid}/aircraft/{aircraftId}`. */
export const AIRCRAFT_SHARES_COLLECTION = "aircraft_shares";
export const SHARE_AIRCRAFT_SUBCOLLECTION = "aircraft";
export const SHARE_MEMBERS_SUBCOLLECTION = "members";
export const SHARE_INVITES_SUBCOLLECTION = "invites";

/**
 * The ACL lives under the HOST, not at a global aircraft-id key (#204).
 *
 * An aircraft id is unique only within one user's tree. Keyed globally, anyone who knew an id could
 * create the single slot for it — honestly naming themselves host — and the rules, looking the ACL up
 * by aircraft id alone, would consult that doc to authorize access to someone else's aircraft.
 * Under the host means a share a caller can create only ever governs the caller's own tree.
 */
export function aircraftShareDocPath(hostUid: string, aircraftId: string): string {
  return `${AIRCRAFT_SHARES_COLLECTION}/${hostUid}/${SHARE_AIRCRAFT_SUBCOLLECTION}/${aircraftId}`;
}

export function shareMemberDocPath(hostUid: string, aircraftId: string, uid: string): string {
  return `${aircraftShareDocPath(hostUid, aircraftId)}/${SHARE_MEMBERS_SUBCOLLECTION}/${uid}`;
}

export function shareInviteDocPath(
  hostUid: string,
  aircraftId: string,
  tokenHash: string,
): string {
  return `${aircraftShareDocPath(hostUid, aircraftId)}/${SHARE_INVITES_SUBCOLLECTION}/${tokenHash}`;
}

/**
 * Root ACL document at `aircraft_shares/{aircraftId}`. Kept tiny and low-contention: rules perform
 * exactly one `get()` on it per shared-scope request to read `member_roles`. The redeem/revoke
 * functions keep `member_roles` transactionally in step with the `members` subcollection.
 */
export type AircraftShareDoc = {
  /** Account whose tree physically holds the aircraft and its records. Also present in member_roles as "owner". */
  hostUid: string;
  /** == the document id; denormalized for collection-group queries. */
  aircraftId: string;
  /** uid → role. Denormalized onto the root so a rule check is a single get(). Includes hostUid → "owner". */
  memberRoles: Record<string, ShareRole>;
  createdAt: Timestamp;
};

/**
 * The technician profile fields a member publishes into the share so other members can select them
 * on a signed log without reading the member's private technician record. See §7. Certificate
 * fields may be empty (e.g. an owner doing FAR 43 preventive maintenance with no A&P certificate).
 */
export type TechnicianMirror = {
  name: string;
  certificateType: string;
  certNumber: string;
  certExpiration?: Timestamp | null;
  certExpireLimit: string;
};

/**
 * One document per member at `aircraft_shares/{aircraftId}/members/{uid}`. Carries display + the
 * technician mirror so the root doc stays small. The member maintains their own display fields and
 * mirror; `role` is immutable from clients (rules enforce this) and function-managed.
 */
export type ShareMemberDoc = {
  role: ShareRole;
  displayName: string;
  photoUrl?: string | null;
  technicianMirror?: TechnicianMirror | null;
  addedAt: Timestamp;
  invitedBy: string;
};

/**
 * A pending invite at `aircraft_shares/{aircraftId}/invites/{tokenHash}`, where
 * `tokenHash = SHA-256(secret)`. Only the hash is stored, so a read of the collection yields
 * nothing redeemable — the secret exists only in the link the owner shares.
 */
export type ShareInviteDoc = {
  role: ShareRole;
  createdBy: string;
  createdAt: Timestamp;
  expiresAt: Timestamp;
  maxUses: number;
  useCount: number;
  revoked: boolean;
};

/**
 * Invite default from the PRD: single-use (the code doc is deleted on redeem). Expiry is not here —
 * it lives with the code in inviteCodes.ts (INVITE_TTL_MS, 1 day), short on purpose since it stands
 * in for the code's entropy.
 */
export const INVITE_DEFAULT_MAX_USES = 1;

/**
 * Pairing-code invite (#164). Defined in inviteCodes.ts, re-exported here so the share types have a
 * single import site.
 */
export type { InviteCodeDoc } from "./inviteCodes.js";
