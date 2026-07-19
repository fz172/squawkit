import { adminDb } from "../config/firebaseAdmin.js";
import { aircraftShareDocPath, type AircraftShareDoc } from "../sharing/sharingModels.js";

/**
 * Shared authorization core for the attachment broker (design §9.2).
 *
 * Blobs live strictly under the host at `users/{hostUid}/aircraft/{acId}/blobs/{blobId}`, and
 * `storage.rules` deny every cross-account access. The broker is the ONLY door across trees, and it
 * opens that door by consulting the `aircraft_shares` ACL — the one thing Storage rules cannot do
 * (they cannot `get()` Firestore). Reads (`streamBlob`) and writes (`getBlobUploadSession`) both
 * authorize through the two functions here, so membership is decided in exactly one place.
 */

/** Canonical object path for a blob in the HOST's tree. The blob namespace is per-aircraft. */
export function blobObjectPath(hostUid: string, acId: string, blobId: string): string {
  return `users/${hostUid}/aircraft/${acId}/blobs/${blobId}`;
}

/** The ACL root for a shared aircraft, or `null` if no share exists at that host+aircraft. */
export async function loadShare(
  hostUid: string,
  acId: string,
): Promise<AircraftShareDoc | null> {
  const snap = await adminDb.doc(aircraftShareDocPath(hostUid, acId)).get();
  return snap.exists ? (snap.data() as AircraftShareDoc) : null;
}

/**
 * Is `uid` a member of this share? The host is present in `memberRoles` as "owner", so this is true
 * for the host too — an owner viewing their own aircraft through the broker is a legitimate caller,
 * not a special case.
 */
export function isShareMember(share: AircraftShareDoc, uid: string): boolean {
  return share.memberRoles?.[uid] != null;
}

/**
 * Whether attachments are enabled for this aircraft (design §9.7). The gate is a property of the
 * aircraft — the host's entitlement projected onto the ACL root — never the caller's own billing.
 *
 * Absent is treated as ENABLED: the entitlement projector (P8.7 #248) is not built yet, so the field
 * will not exist in dogfood, and refusing on absence would break every upload before the paywall
 * even ships. Only an explicit `false` denies, so the moment the projector starts writing the field
 * this begins enforcing with no code change.
 */
export function attachmentsEnabled(share: AircraftShareDoc): boolean {
  return share.attachmentsEnabled !== false;
}

/**
 * Read authorization, resolved fresh on EVERY call (design §9.2.1). Because the read path re-runs
 * this per request and hands out no bearer credential, a revoked member is refused on their very
 * next request — there is no minted URL that outlives the ACL.
 */
export async function authorizeBlobRead(
  callerUid: string,
  hostUid: string,
  acId: string,
): Promise<boolean> {
  const share = await loadShare(hostUid, acId);
  return share != null && isShareMember(share, callerUid);
}
