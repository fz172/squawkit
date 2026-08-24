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
  squawkId: string;
  title: string;
  /** The rank the squawk came from, for `notification_body_priority_raised_single`'s "from X". */
  fromRank: number;
};

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

  return { tier, squawkId: squawk.id, title: squawk.title, fromRank };
}

export { SquawkPriority };
