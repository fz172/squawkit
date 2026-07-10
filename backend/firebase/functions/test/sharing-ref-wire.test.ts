import { describe, expect, it } from "vitest";

import { SharedAircraftRef, ShareRole } from "../src/generated/proto/sharing/shared_aircraft_ref.js";
import { SHARE_ROLE } from "../src/sharing/sharingModels.js";
import {
  encodeSharedAircraftRef,
  sharedAircraftRefTombstone,
  sharedAircraftRefWireDoc,
} from "../src/sharing/sharedAircraftRefWire.js";

const decodePayload = (b64: string) =>
  SharedAircraftRef.decode(new Uint8Array(Buffer.from(b64, "base64")));

describe("shared_aircraft_ref wire encoding", () => {
  it("encodes a ref the client proto can decode back", () => {
    const b64 = encodeSharedAircraftRef("ac-1", "host-1", SHARE_ROLE.TECHNICIAN);
    const decoded = decodePayload(b64);
    expect(decoded.aircraftId).toBe("ac-1");
    expect(decoded.hostUid).toBe("host-1");
    expect(decoded.role).toBe(ShareRole.SHARE_ROLE_TECHNICIAN);
  });

  it("maps the owner role", () => {
    expect(decodePayload(encodeSharedAircraftRef("ac", "h", SHARE_ROLE.OWNER)).role).toBe(
      ShareRole.SHARE_ROLE_OWNER,
    );
  });

  it("produces a live SyncDocWire shape", () => {
    const doc = sharedAircraftRefWireDoc("ac-1", "host-1", SHARE_ROLE.OWNER);
    expect(doc.deleted).toBe(false);
    expect(doc.schema).toBe("sharing.SharedAircraftRef");
    expect(doc.lastUpdateTimestamp).toBeDefined();
    expect(decodePayload(doc.payload as string).aircraftId).toBe("ac-1");
  });

  it("produces a tombstone with no payload", () => {
    const doc = sharedAircraftRefTombstone();
    expect(doc.deleted).toBe(true);
    expect(doc.payload).toBe("");
    expect(doc.schema).toBe("sharing.SharedAircraftRef");
  });
});
