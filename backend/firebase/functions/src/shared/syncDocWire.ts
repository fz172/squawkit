/**
 * The envelope every synced entity revision is stored in, and how to get bytes back out of it.
 *
 * `FirestoreSyncWriter` (the only writer) sets `payload` to `Base64.encode(bytes)` — a **string**,
 * not a Firestore blob — because base64 is the only form that round-trips cleanly through GitLive's
 * commonMain serialization on Android and iOS alike. Bytes are still accepted here so a document
 * seeded directly by the Admin SDK (every emulator test, and any future server-side writer) decodes
 * too.
 */
export type SyncDocWire = {
  /** Proto bytes, base64-encoded. `schema` names the type they decode to. */
  payload?: string | Uint8Array | Buffer;
  schema?: string;
  deleted?: boolean;
  /**
   * Author of this revision, stamped by the writing client and enforced by `writerIsSelf()` in
   * firestore.rules. Absent on pre-attestation documents and on anything a Cloud Function wrote.
   */
  writerUid?: string;
  lastUpdateTimestamp?: unknown;
};

/**
 * Proto bytes for [payload], or `null` when there is nothing decodable.
 *
 * `null` means "unknown", never "empty": a caller must not read it as "this record claims nothing."
 */
export function payloadBytes(payload: SyncDocWire["payload"]): Uint8Array | null {
  if (payload == null) return null;
  if (payload instanceof Uint8Array) return payload; // covers Buffer, which extends Uint8Array
  if (typeof payload === "string") {
    if (payload.length === 0) return null;
    try {
      return new Uint8Array(Buffer.from(payload, "base64"));
    } catch {
      return null;
    }
  }
  return null;
}
