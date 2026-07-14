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
 * nothing — an unknown schema or corrupt bytes must never be read as "this record owns no
 * attachments", because that is indistinguishable from "delete everything it pointed at".
 */
export function blobIdsInPayload(schema: string, payload: Uint8Array): string[] | null {
  try {
    switch (schema) {
      case SCHEMA.MAINTENANCE_LOG:
        return blobIds(MaintenanceLog.decode(payload).attachments);
      case SCHEMA.MAINTENANCE_TASK:
        return blobIds(MaintenanceTask.decode(payload).attachments);
      case SCHEMA.SQUAWK:
        return blobIds(Squawk.decode(payload).attachments);
      default:
        return null; // unknown schema — say so rather than claim it owns nothing
    }
  } catch {
    return null; // corrupt or a version we cannot read — same rule
  }
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
