import { Attachment, AttachmentType } from "../generated/proto/aircraft/attachment.js";
import { MaintenanceLog } from "../generated/proto/aircraft/maintenance_log.js";
import { MaintenanceTask } from "../generated/proto/aircraft/maintenance_task.js";
import { Squawk } from "../generated/proto/aircraft/squawk.js";

/**
 * Which blobs does a record own? (#158)
 *
 * A blob lives at `users/{uid}/aircraft/{acId}/blobs/{blobId}` — the path names the **aircraft** and
 * says nothing about which **record** owns the bytes. That mapping exists in exactly one place: the
 * record's protobuf payload.
 *
 * Security rules cannot read a payload — they see opaque bytes, which is why the sharing ACL exists
 * as plain fields. Cloud Functions **can**, and this is where they do it. The distinction is easy to
 * conflate and it decides the whole design (docs/storage/deletion_gc_design.html §3).
 */

/** The envelope's `schema` field names the type, so a tombstone can be decoded without guessing. */
const SCHEMA = {
  MAINTENANCE_LOG: "aircraft.MaintenanceLog",
  MAINTENANCE_TASK: "aircraft.MaintenanceTask",
  SQUAWK: "aircraft.Squawk",
} as const;

/** Records that can carry attachments. Everything else owns no bytes and is skipped. */
export function schemaCanOwnBlobs(schema: string): boolean {
  return (Object.values(SCHEMA) as string[]).includes(schema);
}

/**
 * Blob ids referenced by [payload], or `null` if it cannot be decoded.
 *
 * **`null` is not "no blobs".** It means we do not know, and the caller must therefore delete
 * nothing — an unknown schema, an unreadable payload shape, or corrupt bytes must never be read as
 * "this record owns no attachments", because that is indistinguishable from "delete everything it
 * pointed at".
 *
 * [payload] is taken in its **stored** form rather than as bytes, on purpose. Getting that
 * conversion wrong is #428 — the bug that deleted real photos — and it went unnoticed because
 * `new Uint8Array("<base64>")` neither throws nor decodes. Two callers each had their own private
 * converter and only one of them was fixed. There is now one, here, next to the decoding it feeds,
 * so a caller cannot hand this function the wrong shape any more.
 */
export function blobIdsInPayload(
  schema: string,
  payload: string | Uint8Array | Buffer | undefined,
): string[] | null {
  const bytes = payloadBytes(payload);
  if (bytes == null) return null;
  try {
    switch (schema) {
      case SCHEMA.MAINTENANCE_LOG:
        return blobIds(MaintenanceLog.decode(bytes).attachments);
      case SCHEMA.MAINTENANCE_TASK:
        return blobIds(MaintenanceTask.decode(bytes).attachments);
      case SCHEMA.SQUAWK:
        return blobIds(Squawk.decode(bytes).attachments);
      default:
        return null; // unknown schema — say so rather than claim it owns nothing
    }
  } catch {
    return null; // corrupt or a version we cannot read — same rule
  }
}

/**
 * Proto bytes for a stored payload, or `null` when there is nothing readable there.
 *
 * `FirestoreSyncWriter` — the only writer — stores `payload` as `Base64.encode(bytes)`, a **string**,
 * because base64 is the only form that round-trips through GitLive's commonMain serialization on
 * Android and iOS alike. Raw bytes are still accepted so a document seeded by the Admin SDK decodes
 * too.
 *
 * An empty string returns `null`, not an empty array. Zero bytes decode to a record with no
 * attachments, and "references nothing" is precisely the verdict that deletes photos — so an empty
 * payload is treated as unreadable rather than as a claim.
 */
function payloadBytes(payload: string | Uint8Array | Buffer | undefined): Uint8Array | null {
  if (payload == null) return null;
  if (payload instanceof Uint8Array) return payload; // Buffer extends Uint8Array
  if (typeof payload !== "string" || payload.length === 0) return null;
  return new Uint8Array(Buffer.from(payload, "base64"));
}

/**
 * A LINK attachment is just a URL living in the payload — it owns no bytes in Storage, so there is
 * nothing to collect. Everything else is backed by a blob whose id IS the attachment id.
 */
function blobIds(attachments: Attachment[]): string[] {
  return attachments
    .filter((a) => a.type !== AttachmentType.ATTACHMENT_TYPE_LINK)
    .map((a) => a.id)
    .filter((id) => id.length > 0);
}
