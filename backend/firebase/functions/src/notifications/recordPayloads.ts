import { logger } from "firebase-functions/v2";

import { Aircraft } from "../generated/proto/aircraft/aircraft.js";
import { Squawk, SquawkDismissReason, SquawkPriority } from "../generated/proto/aircraft/squawk.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";

/**
 * The two things the fan-out needs out of an otherwise opaque entity payload: an aircraft's tail
 * number, and a squawk's urgency (§7.2 step 4).
 *
 * Security rules cannot read a payload — they see bytes, which is why the sharing ACL exists as
 * plain fields. Cloud Functions can, and `blobRefs.ts` already does it for attachment ids. This is
 * the same trick for the notification body.
 *
 * **Record titles are deliberately not decoded for the activity path.** The shipped bodies are
 * "%1$s made %2$d changes to %3$s" (`notification_n1_body_plural`) — an actor, a count and a
 * section, never a record title. §7.3's illustrative "Dave Chen updated a task: Annual Inspection"
 * predates those strings; matching what `strings.xml` actually renders is what keeps a tray entry
 * posted from push identical to one `WebForeignWriteDetector` posts for the same event. The
 * escalation body is the one place a title appears, and it is decoded below.
 */

export const SCHEMA = {
  AIRCRAFT: "aircraft.Aircraft",
  SQUAWK: "aircraft.Squawk",
} as const;

/** Tail number carried by an Aircraft envelope, or `null` if it will not decode. */
export function tailNumberOf(doc: SyncDocWire | undefined): string | null {
  const bytes = payloadBytes(doc?.payload);
  if (bytes == null) return null;
  try {
    const tail = Aircraft.decode(bytes).tailNumber;
    return tail.length > 0 ? tail : null;
  } catch (e) {
    logger.warn("Could not decode an aircraft payload for a notification", { error: String(e) });
    return null;
  }
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

/** The two escalation tiers the server can detect, matching `UrgencyTier` on the client. */
export const ESCALATION_TIER = {
  GROUNDED: "GROUNDED",
  PRIORITY_RAISED: "PRIORITY_RAISED",
} as const;

export type EscalationTier = (typeof ESCALATION_TIER)[keyof typeof ESCALATION_TIER];

export type Escalation = {
  tier: EscalationTier;
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
 * The caller passes `event.params.docId` instead. Decoding is still right for the tail number and
 * the title, which exist only in the payload; the record id has a canonical home in the path.
 */

/**
 * Was this write a reportable priority escalation? (§7.5)
 *
 * Mirrors `SquawkWithStatus.reportableTier()`: **only HIGH and AOG report.** A LOW→MEDIUM bump is a
 * real escalation and still moves N2's watermark, but design §9.2 scopes the *notification* to
 * "becomes high priority or worse" — reporting the quietest possible change would interrupt
 * someone for nothing.
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

  const tier =
    toRank === 4
      ? ESCALATION_TIER.GROUNDED
      : toRank === 3
        ? ESCALATION_TIER.PRIORITY_RAISED
        : null;
  if (tier == null) return null; // MEDIUM and below: below the reportable floor

  // No prior document, or a tombstoned one being written live again: nobody raised anything, the
  // squawk arrived at this rank. Anything else — including a reopen — is a raise on a record that
  // already existed.
  const kind = before == null || before.deleted === true ? "created" : "raised";
  return { tier, title: squawk.title, kind };
}

export { SquawkPriority };
