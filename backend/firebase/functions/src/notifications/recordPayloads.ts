import { logger } from "firebase-functions/v2";

import { Thing } from "../generated/proto/thing/thing.js";
import { MaintenanceLog } from "../generated/proto/thing/maintenance_log.js";
import { MaintenanceTask } from "../generated/proto/thing/maintenance_task.js";
import { Squawk, SquawkDismissReason, SquawkPriority } from "../generated/proto/thing/squawk.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";
import { RECORD_TYPE, type RecordType } from "./notificationModels.js";

/**
 * The things the fan-out needs out of an otherwise opaque entity payload: a Thing's display label,
 * a squawk's urgency (§7.2 step 4), and — since coalescing was removed (design decision,
 * 2026-08-27) — every record type's own title, because the activity path now names the specific
 * record in every push instead of summarizing "N changes."
 *
 * Security rules cannot read a payload — they see bytes, which is why the sharing ACL exists as
 * plain fields. Cloud Functions can, and `blobRefs.ts` already does it for attachment ids. This is
 * the same trick for the notification body.
 */

export const SCHEMA = {
  AIRCRAFT: "thing.Thing",
  SQUAWK: "aircraft.Squawk",
} as const;

/**
 * The label a Thing envelope names itself by, or `null` if it will not decode or nothing names it.
 *
 * Mirrors `Thing.displayLabel` in `core/template` (PRD §9.1): the owner's name, then the template's
 * `title_candidate` field, then make and model, then its `is_identifier` field, then whatever the
 * template does ask for, then the template's own display name. Reading only `tail_number` — which
 * this did until a home's notification opened with a bare ": task is due soon" — leaves every
 * non-airplane Thing nameless.
 *
 * The one addition to the client's order is the final `tail_number` read: a Thing with no DNA can
 * only be an airplane (template_system_design.md §5), and the client gets that fallback from its
 * baked-in registry, which the server does not carry.
 */
export function thingLabelOf(doc: SyncDocWire | undefined): string | null {
  const bytes = payloadBytes(doc?.payload);
  if (bytes == null) return null;
  try {
    return displayLabelOf(Thing.decode(bytes));
  } catch (e) {
    logger.warn("Could not decode a Thing payload for a notification", { error: String(e) });
    return null;
  }
}

function displayLabelOf(thing: Thing): string | null {
  // From `spec`, not the retired field 5 (#668) — the same keys the clients read.
  const spec = (key: string) => thing.spec.find(entry => entry.key === key)?.value ?? "";
  const fields = thing.template?.specFields ?? [];
  const candidates = [
    () => thing.name,
    () => fields.filter(f => f.titleCandidate).map(f => spec(f.key))[0] ?? "",
    () => [spec("make"), spec("model")].filter(v => v.length > 0).join(" "),
    () => fields.filter(f => f.isIdentifier).map(f => spec(f.key))[0] ?? "",
    () => fields.map(f => spec(f.key)).find(v => v.length > 0) ?? "",
    () => thing.template?.displayName ?? "",
    () => spec("tail_number"),
  ];
  for (const candidate of candidates) {
    const value = candidate().trim();
    if (value.length > 0) return value;
  }
  return null;
}

function decodeSquawk(doc: SyncDocWire | undefined): Squawk | null {
  const bytes = payloadBytes(doc?.payload);
  if (bytes == null) return null;
  try {
    return Squawk.decode(bytes);
  } catch (e) {
    logger.warn("Could not decode a squawk payload for a notification", { error: String(e) });
    return null;
  }
}

/**
 * The record's own title/description, resolved per [RecordType] — the specific thing a concrete
 * activity notification names. `null` for `aircraft` (there is no per-record title; the Thing's
 * own identity is its label, via [thingLabelOf]) and whenever the payload will not decode.
 */
export function recordTitleOf(recordType: RecordType, doc: SyncDocWire | undefined): string | null {
  const bytes = payloadBytes(doc?.payload);
  if (bytes == null) return null;
  try {
    switch (recordType) {
      case RECORD_TYPE.SQUAWK: {
        const title = Squawk.decode(bytes).title;
        return title.length > 0 ? title : null;
      }
      case RECORD_TYPE.TASK: {
        const title = MaintenanceTask.decode(bytes).title;
        return title.length > 0 ? title : null;
      }
      case RECORD_TYPE.LOG: {
        const description = MaintenanceLog.decode(bytes).workDescription;
        return description.length > 0 ? description : null;
      }
      case RECORD_TYPE.AIRCRAFT:
        return null;
    }
  } catch (e) {
    logger.warn("Could not decode a record payload for a notification", { recordType, error: String(e) });
    return null;
  }
}

/**
 * `UrgencyRank` for a squawk, the same ladder `UrgencyRank.kt` defines — the only ladder the server
 * can evaluate, and the one N2 already compares watermarks against.
 *
 * Rank 0 is "resolved": addressed, dismissed, tombstoned, or absent. Reopening therefore needs no
 * special case — it restores the stored priority, so the rank goes 0 → 1..4 and the plain
 * `after > before` test fires on its own. An open squawk is never rank 0: an unset priority is
 * still an open defect, so UNKNOWN maps to 1 alongside LOW.
 */
export const RANK_RESOLVED = 0;

export function urgencyRankOf(doc: SyncDocWire | undefined): number {
  if (doc == null || doc.deleted === true) return RANK_RESOLVED;
  const squawk = decodeSquawk(doc);
  if (squawk == null) return RANK_RESOLVED;
  if (squawk.addressedByLogId.length > 0) return RANK_RESOLVED;
  if (squawk.dismissReason !== SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN) {
    return RANK_RESOLVED;
  }
  switch (squawk.priority) {
    case SquawkPriority.SQUAWK_PRIORITY_AOG:
      return 4;
    case SquawkPriority.SQUAWK_PRIORITY_HIGH:
      return 3;
    case SquawkPriority.SQUAWK_PRIORITY_MEDIUM:
      return 2;
    default:
      return 1; // LOW, UNKNOWN — see the doc comment
  }
}

export type Escalation = {
  title: string;
  /**
   * Whether the squawk arrived at this rank by being **created** there or by being **raised** to it.
   *
   * The two read completely differently — "Dave created a new squawk issue" versus "Dave raised the
   * priority of 1 squawk issue" — and only the server can tell them apart, because only it sees the
   * before/after pair. A reopened squawk (dismissal cleared) counts as raised: the record was
   * already there.
   */
  kind: "created" | "raised";
};

/**
 * **Deliberately no `squawkId`.** The payload carries an `id` field, and it is the wrong one to use:
 * the record's identity is its Firestore document id, which comes from the trigger path, while the
 * copy inside the payload is opaque bytes that no rule can check (`writerIsSelf()` attests the
 * envelope, not the contents). Nothing enforces that the two agree.
 *
 * Two ways that bites, and both defeat the one property §7.5 exists to guarantee. proto3 has no
 * field presence for scalars, so an unset `id` decodes to `""` — no error — and every squawk on the
 * aircraft would then post under `n1esc:{ac}:`, each grounding alert replacing the last. And since
 * rules let any member write squawks into a shared aircraft but cannot see inside a payload, a
 * member could carry *another* squawk's id and overwrite that alert in everyone's tray.
 *
 * The caller passes `event.params.docId` instead. Decoding is still right for the thing label and
 * the title, which exist only in the payload; the record id has a canonical home in the path.
 */

/**
 * Was this write a reportable priority escalation? (§7.5)
 *
 * Mirrors `SquawkWithStatus.reportableTier()`: **only HIGH and AOG report, and both report the
 * same way** — AOG is not its own tier, just the top of the same priority-raised ladder (design
 * decision, 2026-08-26). A LOW→MEDIUM bump is a real escalation and still moves N2's watermark, but
 * design §9.2 scopes the *notification* to "becomes high priority or worse" — reporting the
 * quietest possible change would interrupt someone for nothing.
 *
 * An absent `before` (a squawk created straight at HIGH or AOG) reports too, and reads correctly:
 * N2 does the same thing, treating an unseen record's previous rank as `RESOLVED`.
 */
export function escalationOf(
  before: SyncDocWire | undefined,
  after: SyncDocWire | undefined,
): Escalation | null {
  if (after == null || after.deleted === true) return null;
  const squawk = decodeSquawk(after);
  if (squawk == null) return null;

  const fromRank = urgencyRankOf(before);
  const toRank = urgencyRankOf(after);
  if (toRank <= fromRank) return null; // unchanged, or de-escalated — silent by design (PRD §6)
  if (toRank < 3) return null; // MEDIUM and below: below the reportable floor

  // No prior document, or a tombstoned one being written live again: nobody raised anything, the
  // squawk arrived at this rank. Anything else — including a reopen — is a raise on a record that
  // already existed.
  const kind = before == null || before.deleted === true ? "created" : "raised";
  return { title: squawk.title, kind };
}

export { SquawkPriority };
