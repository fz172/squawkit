import { FieldValue } from "firebase-admin/firestore";
import type { DocumentData } from "firebase-admin/firestore";

import {
  ShareRole as ProtoShareRole,
  SharedAircraftRef,
} from "../generated/proto/sharing/shared_aircraft_ref.js";
import { SHARE_ROLE, type ShareRole } from "./sharingModels.js";

/**
 * Builds the SyncDocWire document a Cloud Function writes to
 * `users/{memberUid}/shared_aircraft_ref/{thingId}` so the member's sync engine hydrates the
 * shared aircraft. The shape must match the client's SyncDocWire (feature/sync SyncDocWire.kt):
 * base64 proto payload + deleted + schema + server-stamped lastUpdateTimestamp. writerUid is
 * omitted — function-written docs are unattested and the field is nullable client-side. See
 * docs/sharing §2.2.
 */

const SCHEMA = "sharing.SharedAircraftRef";

function toProtoRole(role: ShareRole): ProtoShareRole {
  return role === SHARE_ROLE.OWNER
    ? ProtoShareRole.SHARE_ROLE_OWNER
    : ProtoShareRole.SHARE_ROLE_TECHNICIAN;
}

/** Encode a SharedAircraftRef proto to the base64 payload the client's WireCodec decodes. */
export function encodeSharedAircraftRef(
  thingId: string,
  hostUid: string,
  role: ShareRole,
): string {
  const bytes = SharedAircraftRef.encode({
    aircraftId: thingId,
    hostUid,
    role: toProtoRole(role),
  }).finish();
  return Buffer.from(bytes).toString("base64");
}

/** A live ref document pointing the member at a shared aircraft. */
export function sharedAircraftRefWireDoc(
  thingId: string,
  hostUid: string,
  role: ShareRole,
): DocumentData {
  return {
    payload: encodeSharedAircraftRef(thingId, hostUid, role),
    deleted: false,
    schema: SCHEMA,
    lastUpdateTimestamp: FieldValue.serverTimestamp(),
  };
}

/**
 * Reads back the host and aircraft a live ref points at, or null for a tombstone or unreadable doc.
 *
 * The inverse of [sharedAircraftRefWireDoc]. Needed because a member's refs are the only index of
 * which shares they belong to: the ACL stores `memberRoles` as a map, which Firestore cannot query
 * by key, so "every share this user is in" is answerable only from their own tree.
 *
 * Returns null rather than throwing on a malformed payload — this feeds account deletion, and one
 * unreadable ref must not be able to strand the rest of the teardown.
 */
export function decodeSharedAircraftRef(
  doc: DocumentData,
): { thingId: string; hostUid: string } | null {
  if (doc?.deleted === true) return null;
  const payload = doc?.payload;
  if (typeof payload !== "string" || payload.length === 0) return null;
  try {
    const ref = SharedAircraftRef.decode(Buffer.from(payload, "base64"));
    if (!ref.aircraftId || !ref.hostUid) return null;
    return { thingId: ref.aircraftId, hostUid: ref.hostUid };
  } catch {
    return null;
  }
}

/** A tombstone that tells the ex-member's devices to purge the shared aircraft (no payload needed). */
export function sharedAircraftRefTombstone(): DocumentData {
  return {
    payload: "",
    deleted: true,
    schema: SCHEMA,
    lastUpdateTimestamp: FieldValue.serverTimestamp(),
  };
}
