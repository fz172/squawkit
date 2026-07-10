import { FieldValue } from "firebase-admin/firestore";
import type { DocumentData } from "firebase-admin/firestore";

import {
  ShareRole as ProtoShareRole,
  SharedAircraftRef,
} from "../generated/proto/sharing/shared_aircraft_ref.js";
import { SHARE_ROLE, type ShareRole } from "./sharingModels.js";

/**
 * Builds the SyncDocWire document a Cloud Function writes to
 * `users/{memberUid}/shared_aircraft_ref/{aircraftId}` so the member's sync engine hydrates the
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
  aircraftId: string,
  hostUid: string,
  role: ShareRole,
): string {
  const bytes = SharedAircraftRef.encode({
    aircraftId,
    hostUid,
    role: toProtoRole(role),
  }).finish();
  return Buffer.from(bytes).toString("base64");
}

/** A live ref document pointing the member at a shared aircraft. */
export function sharedAircraftRefWireDoc(
  aircraftId: string,
  hostUid: string,
  role: ShareRole,
): DocumentData {
  return {
    payload: encodeSharedAircraftRef(aircraftId, hostUid, role),
    deleted: false,
    schema: SCHEMA,
    lastUpdateTimestamp: FieldValue.serverTimestamp(),
  };
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
