import type { DocumentSnapshot } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import type { Change as FirestoreChange, FirestoreEvent } from "firebase-functions/v2/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

import { FUNCTION_REGION } from "../config/env.js";
import {
  ENTITY_SEGMENT_LEGACY,
  ENTITY_SEGMENT_THING,
  entityDocPath,
  type EntitySegment,
} from "../config/entitySegment.js";
import { adminDb } from "../config/firebaseAdmin.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";
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
  type PushData,
} from "./pushMessages.js";
import { enabledTokensFor, sendPush, type PushTarget } from "./pushSender.js";
import { escalationOf, recordTitleOf, tailNumberOf, type Escalation } from "./recordPayloads.js";

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
 *
 * **Every write sends its own concrete notification** (design decision, 2026-08-27) — there is no
 * counter, no session, no throttle, no hourly ceiling. See [notificationModels.activityNotificationId]
 * for what the earlier, coalescing design got wrong.
 */

type WriteEvent = FirestoreEvent<
  FirestoreChange<DocumentSnapshot> | undefined,
  Record<string, string>
>;

/**
 * MIGRATION (thing_migration_design.md §2.7 / task B9): both triggers below are registered twice,
 * once per entity segment — a v2 Firestore trigger path is a deploy-time literal with no "either
 * segment" wildcard, and a deploy is global. The original export names stay bound to the legacy
 * segment so the already-deployed functions are not torn down and recreated; Phase F3 deletes them.
 *
 * [segment] is threaded into the handler because the tail-number read below builds an entity path,
 * which must stay in the tree the write happened in.
 */
const handleRecordWritten =
  (segment: EntitySegment) =>
  async (event: WriteEvent) => {
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

    // A squawk write that raises priority bypasses activity entirely (§7.2 step 5): it is the one
    // change important enough that it gets its own body and its own notification, exempt from
    // nothing.
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
        segment,
      );
      return;
    }

    await fanOutActivity({
      hostUid,
      aircraftId,
      recordType,
      recordId: event.params.docId,
      recordTitle: recordTitleOf(recordType, change.after) ?? "",
      kind: activityKindOf(change),
      actorUid: change.actorUid,
      nowMs,
      recipients,
      segment,
    });
  };

// MIGRATION (thing_migration_design.md §2.7c / task B9): deployed with C2, NOT with the Phase A/B
// branch. A cutover copy CREATES each document, so `before` never exists and every record the Phase
// D script copies would read as a fresh authored write here. By C2 the copy is done, and nothing
// writes `/thing/` until E2 anyway — so these are inert before this point and correct after it.
export const onNotifiableThingRecordWritten = onDocumentWritten(
  {
    document: `users/{uid}/${ENTITY_SEGMENT_THING}/{acId}/{kind}/{docId}`,
    region: FUNCTION_REGION,
  },
  handleRecordWritten(ENTITY_SEGMENT_THING),
);

/**
 * The Aircraft record itself — a tail number or a make/model correction is collaboration activity
 * too, and `collaboration_disabled` is the toggle that governs it.
 *
 * A tombstone write is skipped: deleting an aircraft tears down the share (`onAircraftDeleted`), so
 * "someone made a change to the aircraft" would be both wrong and the last thing the recipient ever
 * heard about it. That also means this trigger's writes are always "updated" — never created (the
 * aircraft already exists once it can be shared) or deleted (filtered here).
 */
const handleAircraftWritten =
  (segment: EntitySegment) =>
  async (event: WriteEvent) => {
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
      hostUid,
      aircraftId,
      recordType: RECORD_TYPE.AIRCRAFT,
      recordId: aircraftId,
      recordTitle: "",
      kind: "updated",
      actorUid: change.actorUid,
      nowMs: Date.now(),
      recipients,
      segment,
      // The write in hand IS the aircraft, so its tail number needs no second read.
      tailNumber: tailNumberOf(change.after) ?? undefined,
    });
  };

// MIGRATION (task F3): both `aircraft`-path registrations are gone — see onAircraftDeleted.

export const onNotifiableThingWritten = onDocumentWritten(
  { document: `users/{uid}/${ENTITY_SEGMENT_THING}/{acId}`, region: FUNCTION_REGION },
  handleAircraftWritten(ENTITY_SEGMENT_THING),
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

/** Created (no prior doc), deleted (soft-tombstoned), or a plain update — what the body says happened. */
function activityKindOf(change: Change): "created" | "updated" | "deleted" {
  if (change.after.deleted === true) return "deleted";
  if (change.before == null || change.before.deleted === true) return "created";
  return "updated";
}

// --- Fan-out -----------------------------------------------------------------------------------

type ActivityFanOut = {
  hostUid: string;
  aircraftId: string;
  recordType: RecordType;
  recordId: string;
  recordTitle: string;
  kind: "created" | "updated" | "deleted";
  actorUid: string;
  nowMs: number;
  recipients: string[];
  tailNumber?: string;
  /** The entity segment the triggering write landed on — see config/entitySegment.ts. */
  segment: EntitySegment;
};

/** One concrete notification per write, naming the record and what happened to it. */
async function fanOutActivity(input: ActivityFanOut): Promise<void> {
  const { hostUid, aircraftId, recordType, recordId, recordTitle, kind, actorUid, nowMs, recipients } =
    input;

  const label = input.tailNumber ?? (await readTailNumber(hostUid, aircraftId, input.segment));
  const actorName = await readActorDisplayName(hostUid, aircraftId, actorUid);

  const sent = await fanOut(
    recipients,
    honorsActivity,
    activityPushData({
      aircraftId,
      recordType,
      recordId,
      recordTitle,
      kind,
      actorUid,
      actorName,
      tailNumber: label,
      atMs: nowMs,
    }),
  );

  logger.info("N1 activity fan-out", {
    aircraftId,
    recordType,
    recordId,
    kind,
    recipients: recipients.length,
    sent,
  });
}

/**
 * The §7.5 bypass, exempt from nothing else here either: it never touched the activity counter this
 * design removed, and it still doesn't touch anything that replaced it.
 */
async function fanOutEscalation(
  hostUid: string,
  aircraftId: string,
  /** The record's Firestore document id — its authoritative identity. */
  squawkId: string,
  actorUid: string,
  recipients: string[],
  escalation: Escalation,
  segment: EntitySegment,
): Promise<void> {
  const [tailNumber, actorName] = await Promise.all([
    readTailNumber(hostUid, aircraftId, segment),
    readActorDisplayName(hostUid, aircraftId, actorUid),
  ]);

  const sent = await fanOut(
    recipients,
    (settings) => honorsEscalation(settings),
    escalationPushData({
      aircraftId,
      squawkId,
      title: escalation.title,
      kind: escalation.kind,
      tailNumber,
      actorName,
    }),
  );

  logger.info("N1 escalation fan-out", {
    aircraftId,
    squawkId,
    kind: escalation.kind,
    recipients: recipients.length,
    sent,
  });
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
      // succeeded — hears nothing. The triggers do not retry, so that notification is simply gone.
      // One unreachable recipient must cost only that recipient.
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
async function readTailNumber(
  hostUid: string,
  aircraftId: string,
  segment: EntitySegment,
): Promise<string> {
  try {
    const snap = await adminDb.doc(entityDocPath(hostUid, aircraftId, segment)).get();
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
