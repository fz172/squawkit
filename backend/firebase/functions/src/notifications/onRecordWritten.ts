import { logger } from "firebase-functions/v2";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";
import {
  bumpActivity,
  ceilingTripped,
  markActivitySent,
  readRateState,
  recordSend,
  type BumpInput,
} from "./activityCounter.js";
import {
  honorsActivity,
  honorsEscalation,
  readActorDisplayName,
  readNotificationSettings,
  readShareAudience,
} from "./audience.js";
import { RECORD_TYPE, recordTypeForKind, type RecordType } from "./notificationModels.js";
import {
  activityPushData,
  escalationPushData,
  highVolumePushData,
  type PushData,
} from "./pushMessages.js";
import { enabledTokensFor, sendPush, type PushTarget } from "./pushSender.js";
import { escalationOf, tailNumberOf, type Escalation } from "./recordPayloads.js";

/**
 * N1 collaboration fan-out (docs/notifications/notifications_design.md §7.2).
 *
 * Two triggers over the paths a collaborator's edit lands on: the per-aircraft record subtree — the
 * same path `onRecordDeleted` already watches — and the aircraft document itself.
 *
 * Three properties do the security work, and none of them is a check that has to be remembered:
 *
 * - **`hostUid` comes from the path.** Unspoofable, the same property `firestore.rules` leans on.
 * - **The actor comes from `writerUid` on the envelope**, which rules require to equal the writing
 *   client's own uid. Never from anything else the client supplied.
 * - **The audience is re-derived on every send**, so a member revoked between two writes is absent
 *   from the second fan-out without anything having to notice the revocation.
 *
 * And one property does the cost work: reading the ACL is the **first** thing that happens after the
 * cheap local checks, and most writes are on unshared aircraft, so the common case is one document
 * read and nothing else.
 */

export const onNotifiableRecordWritten = onDocumentWritten(
  { document: "users/{uid}/aircraft/{acId}/{kind}/{docId}", region: FUNCTION_REGION },
  async (event) => {
    const hostUid = event.params.uid;
    const aircraftId = event.params.acId;
    const recordType = recordTypeForKind(event.params.kind);
    if (recordType == null) return; // maintenance_overview and anything else: not notifiable

    const change = readChange(event);
    if (change == null) return;

    const audience = await readShareAudience(hostUid, aircraftId);
    if (audience == null) return; // unshared — the early exit that keeps this cheap

    const recipients = audience.memberUids.filter((uid) => uid !== change.actorUid);
    if (recipients.length === 0) return; // the actor is the only member

    const nowMs = Date.now();

    // A squawk write that raises priority bypasses the counter entirely (§7.2 step 5): it is the one
    // change important enough that a count of it would be the wrong thing to say.
    const escalation =
      recordType === RECORD_TYPE.SQUAWK ? escalationOf(change.before, change.after) : null;
    if (escalation != null) {
      // The squawk id comes from the PATH, never from the payload's own `id` field — see
      // `escalationOf`'s note on why the two are not interchangeable.
      await fanOutEscalation(
        hostUid,
        aircraftId,
        event.params.docId,
        change.actorUid,
        recipients,
        escalation,
      );
      return;
    }

    await fanOutActivity({
      input: { hostUid, aircraftId, recordType, actorUid: change.actorUid, nowMs },
      recipients,
    });
  },
);

/**
 * The Aircraft record itself — a tail number or a make/model correction is collaboration activity
 * too, and `aircraft_activity_disabled` is the toggle that governs it.
 *
 * A tombstone write is skipped: deleting an aircraft tears down the share (`onAircraftDeleted`), so
 * "someone made a change to the aircraft" would be both wrong and the last thing the recipient ever
 * heard about it.
 */
export const onNotifiableAircraftWritten = onDocumentWritten(
  { document: "users/{uid}/aircraft/{acId}", region: FUNCTION_REGION },
  async (event) => {
    const hostUid = event.params.uid;
    const aircraftId = event.params.acId;

    const change = readChange(event);
    if (change == null) return;
    if (change.after.deleted === true) return;

    const audience = await readShareAudience(hostUid, aircraftId);
    if (audience == null) return;

    const recipients = audience.memberUids.filter((uid) => uid !== change.actorUid);
    if (recipients.length === 0) return;

    await fanOutActivity({
      input: {
        hostUid,
        aircraftId,
        recordType: RECORD_TYPE.AIRCRAFT,
        actorUid: change.actorUid,
        nowMs: Date.now(),
      },
      recipients,
      // The write in hand IS the aircraft, so its tail number needs no second read.
      tailNumber: tailNumberOf(change.after) ?? undefined,
    });
  },
);

// --- The write itself --------------------------------------------------------------------------

type Change = {
  before: SyncDocWire | undefined;
  after: SyncDocWire;
  actorUid: string;
};

/**
 * The parts of a trigger event this fan-out acts on, or `null` when the write is not a person's
 * edit.
 *
 * Three things are filtered out here, all of them cheap and all of them local:
 *
 * - **A hard delete.** `after` is gone, so this is the tombstone GC reclaiming a record months
 *   later, not somebody touching it.
 * - **A write with no `writerUid`.** A pre-attestation document, or a Cloud Function write that
 *   creates a document outright. Note what this does **not** catch: `onAircraftDeleted`'s tombstone
 *   cascade uses `batch.update`, which *preserves* the existing `writerUid`, so those writes look
 *   exactly like the original author deleting each record. What actually keeps them silent is that
 *   `onAircraftDeleted` tears the share down *before* it tombstones the children, so the ACL is gone
 *   and `readShareAudience` returns null. That ordering is load-bearing for this feature and is
 *   flagged as such in `onAircraftDeleted`; the test below pins the real cascade shape rather than a
 *   hand-stripped one.
 * - **A write that changed nothing.** A client re-pushing an identical revision is not news.
 */
function readChange(event: {
  data?: { before?: { exists: boolean; data(): unknown }; after?: { exists: boolean; data(): unknown } };
}): Change | null {
  const afterSnap = event.data?.after;
  if (afterSnap == null || !afterSnap.exists) return null;
  const after = afterSnap.data() as SyncDocWire;

  const actorUid = after.writerUid ?? "";
  if (actorUid.length === 0) return null;

  const beforeSnap = event.data?.before;
  const before = beforeSnap?.exists === true ? (beforeSnap.data() as SyncDocWire) : undefined;
  if (before != null && !isMeaningfulChange(before, after)) return null;

  return { before, after, actorUid };
}

function isMeaningfulChange(before: SyncDocWire, after: SyncDocWire): boolean {
  if ((before.deleted === true) !== (after.deleted === true)) return true;
  return base64Of(before.payload) !== base64Of(after.payload);
}

function base64Of(payload: SyncDocWire["payload"]): string {
  const bytes = payloadBytes(payload);
  return bytes == null ? "" : Buffer.from(bytes).toString("base64");
}

// --- Fan-out -----------------------------------------------------------------------------------

type ActivityFanOut = {
  input: BumpInput;
  recipients: string[];
  tailNumber?: string;
};

/** §7.4 steps 3–7, in order, and the order is the point: the ceiling is checked before any write. */
async function fanOutActivity({ input, recipients, tailNumber }: ActivityFanOut): Promise<void> {
  const { hostUid, aircraftId, recordType, actorUid, nowMs } = input;

  const rate = await readRateState(hostUid, aircraftId, nowMs);
  if (ceilingTripped(rate)) {
    // Past the cap this costs one read and nothing else — no counter write, no audience walk.
    if (rate.ceilingNotified) return;
    await fanOutHighVolume(hostUid, aircraftId, recipients, nowMs, tailNumber);
    return;
  }

  const bump = await bumpActivity(input);
  if (bump.throttled) {
    logger.debug("N1 counted but throttled", { aircraftId, recordType, actorUid });
    return;
  }

  const label = tailNumber ?? bump.cachedAircraftLabel ?? (await readTailNumber(hostUid, aircraftId));
  const actorName = await readActorDisplayName(hostUid, aircraftId, actorUid);

  const sent = await fanOut(
    recipients,
    (settings) => honorsActivity(settings, recordType),
    activityPushData({
      aircraftId,
      recordType,
      actorUid,
      actorName,
      tailNumber: label,
      changeCount: bump.changeCount,
      sessionSeq: bump.sessionSeq,
    }),
  );

  // Stamped whether or not anything went out: the send pass ran, and throttling the next write
  // against it is what stops a burst re-walking the whole audience 200 times.
  await markActivitySent(input, { aircraftLabel: label, actorDisplayName: actorName });
  if (sent > 0) await recordSend(hostUid, aircraftId, nowMs);

  logger.info("N1 activity fan-out", {
    aircraftId,
    recordType,
    changeCount: bump.changeCount,
    recipients: recipients.length,
    sent,
  });
}

/**
 * The §7.5 bypass. Exempt from `MIN_REPOST_INTERVAL` and from the hourly ceiling, and it never
 * touches the activity counter — a grounding alert is not one of "4 changes to squawks".
 *
 * **Exempt from being *blocked*, not from being *counted*.** It still calls `recordSend`, so a storm
 * of escalations consumes the aircraft's hourly budget and quiets routine activity — which is the
 * right order of sacrifice, since "made 3 changes to tasks" is noise while an aircraft is being
 * grounded repeatedly. Without that, escalations were invisible to the accounting entirely.
 *
 * What remains unbounded, stated rather than papered over: nothing caps escalations *themselves*. A
 * client toggling one squawk LOW→AOG→LOW in a loop fires a full fan-out per cycle. §7.5 forbids the
 * two throttles that exist, deliberately — a grounding alert must not be suppressed — so bounding
 * this needs a guard that cannot suppress a *different* squawk's alert (a per-`n1esc:` repost window
 * is the obvious candidate, since a repeat under the same id only replaces itself in the tray).
 * That is a design change, not a bug fix, and it is not made here.
 */
async function fanOutEscalation(
  hostUid: string,
  aircraftId: string,
  /** The record's Firestore document id — its authoritative identity. */
  squawkId: string,
  actorUid: string,
  recipients: string[],
  escalation: Escalation,
): Promise<void> {
  const [tailNumber, actorName] = await Promise.all([
    readTailNumber(hostUid, aircraftId),
    readActorDisplayName(hostUid, aircraftId, actorUid),
  ]);

  const sent = await fanOut(
    recipients,
    (settings) => honorsEscalation(settings, escalation.tier),
    escalationPushData({
      aircraftId,
      squawkId,
      tier: escalation.tier,
      title: escalation.title,
      fromRank: escalation.fromRank,
      tailNumber,
      actorName,
    }),
  );

  // Counted, never blocking: readRateState is not consulted on this path.
  if (sent > 0) await recordSend(hostUid, aircraftId, Date.now());

  logger.info("N1 escalation fan-out", {
    aircraftId,
    squawkId,
    tier: escalation.tier,
    recipients: recipients.length,
    sent,
  });
}

/** One "N4589T · a lot of activity" per aircraft per hour, then silence for the rest of it. */
async function fanOutHighVolume(
  hostUid: string,
  aircraftId: string,
  recipients: string[],
  nowMs: number,
  tailNumber?: string,
): Promise<void> {
  const label = tailNumber ?? (await readTailNumber(hostUid, aircraftId));
  const sent = await fanOut(
    recipients,
    // It reports a volume rather than a class of change, so anyone who wants *any* activity gets it.
    (settings) =>
      honorsActivity(settings, RECORD_TYPE.AIRCRAFT) ||
      honorsActivity(settings, RECORD_TYPE.SQUAWK) ||
      honorsActivity(settings, RECORD_TYPE.TASK) ||
      honorsActivity(settings, RECORD_TYPE.LOG),
    highVolumePushData(aircraftId, label, nowMs),
  );
  await recordSend(hostUid, aircraftId, nowMs, { ceilingNotified: true });
  logger.warn("N1 hourly ceiling tripped", { aircraftId, recipients: recipients.length, sent });
}

/**
 * Collects the tokens of every recipient who has asked for this class, then sends once.
 *
 * Preferences are read here rather than cached anywhere, per §7.4 step 6: preferences and audience
 * are re-derived on every send, which is what makes PRD §9.5 a property of the shape instead of a
 * rule somebody has to enforce.
 */
async function fanOut(
  recipients: string[],
  wants: (settings: Awaited<ReturnType<typeof readNotificationSettings>>) => boolean,
  data: PushData,
): Promise<number> {
  const perRecipient = await Promise.all(
    recipients.map(async (uid): Promise<PushTarget[]> => {
      // Per recipient, not per fan-out. Without this a single transient Firestore error reading one
      // person's push_devices rejects the whole Promise.all, and every OTHER recipient — whose reads
      // succeeded — hears nothing. The triggers do not retry (see activityCounter.ts), so that
      // notification is simply gone. One unreachable recipient must cost only that recipient.
      try {
        const settings = await readNotificationSettings(uid);
        if (!wants(settings)) return [];
        return await enabledTokensFor(uid);
      } catch (e) {
        logger.warn("Could not resolve a recipient for a notification", {
          uid,
          notificationId: data.notificationId,
          error: String(e),
        });
        return [];
      }
    }),
  );
  return sendPush(perRecipient.flat(), data);
}

/**
 * The aircraft's tail number, or `""` when it will not resolve.
 *
 * Empty is passed through rather than substituted with the id: the title is "%1$s · Squawks", and a
 * raw UUID there is worse than a title that simply reads "Squawks".
 */
async function readTailNumber(hostUid: string, aircraftId: string): Promise<string> {
  try {
    const snap = await adminDb.doc(`users/${hostUid}/aircraft/${aircraftId}`).get();
    if (!snap.exists) return "";
    return tailNumberOf(snap.data() as SyncDocWire) ?? "";
  } catch (e) {
    logger.warn("Could not read a tail number for a notification", {
      hostUid,
      aircraftId,
      error: String(e),
    });
    return "";
  }
}
