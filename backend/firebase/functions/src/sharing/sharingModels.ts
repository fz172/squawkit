import type { Timestamp } from "firebase-admin/firestore";

/**
 * Schema for the `thing_shares` ACL tree — the plain-field (non-entity) documents that govern
 * Thing sharing. These live OUTSIDE the opaque SyncDocWire entity path precisely so that
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

/**
 * Collection and subcollection names under `thing_shares/{hostUid}/thing/{thingId}`.
 *
 * These three have to agree or every share lookup resolves to a document that does not exist,
 * which a member experiences as being removed: these constants, `firestore.rules`' `shareRole()`,
 * and `SharingManagerImpl`'s `SHARES` / `SHARE_THING`. They were flipped together at G3.
 */
export const THING_SHARES_COLLECTION = "thing_shares";
export const SHARE_THING_SUBCOLLECTION = "thing";
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
export function thingShareDocPath(hostUid: string, thingId: string): string {
  return `${THING_SHARES_COLLECTION}/${hostUid}/${SHARE_THING_SUBCOLLECTION}/${thingId}`;
}

export function shareMemberDocPath(hostUid: string, thingId: string, uid: string): string {
  return `${thingShareDocPath(hostUid, thingId)}/${SHARE_MEMBERS_SUBCOLLECTION}/${uid}`;
}

export function shareInviteDocPath(
  hostUid: string,
  thingId: string,
  tokenHash: string,
): string {
  return `${thingShareDocPath(hostUid, thingId)}/${SHARE_INVITES_SUBCOLLECTION}/${tokenHash}`;
}

/**
 * Root ACL document at `aircraft_shares/{thingId}`. Kept tiny and low-contention: rules perform
 * exactly one `get()` on it per shared-scope request to read `member_roles`. The redeem/revoke
 * functions keep `member_roles` transactionally in step with the `members` subcollection.
 */
export type ThingShareDoc = {
  /** Account whose tree physically holds the aircraft and its records. Also present in member_roles as "owner". */
  hostUid: string;
  /** == the document id; denormalized for collection-group queries. */
  thingId: string;
  /** uid → role. Denormalized onto the root so a rule check is a single get(). Includes hostUid → "owner". */
  memberRoles: Record<string, ShareRole>;
  createdAt: Timestamp;
  /**
   * The host's attachment entitlement, projected onto the ACL root so a member can read it WITHOUT
   * reading the host's billing (design §9.7, P8.7 #248). Maintained by a function when the host's
   * subscription changes. Absent means "not yet projected" — treated as enabled while the projector
   * is unbuilt (the P8.2 broker only refuses on an explicit `false`), so uploads work in dogfood
   * before P8.7 lands and flip to enforcing once it does.
   */
  attachmentsEnabled?: boolean;
};

/**
 * The technician profile fields a member publishes into the share so other members can select them
 * on a signed log without reading the member's private technician record. See §7. Credentials may
 * be empty (e.g. an owner doing FAR 43 preventive maintenance with no A&P certificate).
 */
export type TechnicianMirror = {
  name: string;
  /**
   * Every credential the member holds — one person can be both an A&P and an ASE mechanic (#684).
   * What a current client publishes.
   */
  certifications?: CertificationMirror[];
  /**
   * The single FAA certificate a mirror carried before #684. Read-only: still written by clients
   * that have not updated, never by a current one.
   */
  certificateType: string;
  certNumber: string;
  certExpiration?: Timestamp | null;
  certExpireLimit: string;
};

/** One credential, flattened the same way and for the same reason as its parent. */
export type CertificationMirror = {
  /** The `CertificationDef.key` a template declares, or `custom_N` for one the user named. */
  type: string;
  number: string;
  expiration?: Timestamp | null;
  expireLimit: string;
  /** The member's own word, on a `custom_N` credential only. */
  label?: string;
};

/**
 * One document per member at `aircraft_shares/{thingId}/members/{uid}`. Carries display + the
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
 * A pending invite at `aircraft_shares/{thingId}/invites/{tokenHash}`, where
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
