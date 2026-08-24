import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { adminDb } from "../config/firebaseAdmin.js";
import {
  ACTIVITY_WINDOW_MS,
  AIRCRAFT_HOURLY_CEILING,
  MIN_REPOST_INTERVAL_MS,
  activityDocPath,
  rateDocPath,
  type NotificationActivityDoc,
  type NotificationRateDoc,
  type RecordType,
} from "./notificationModels.js";

/**
 * The `notification_activity` counter and the `notification_rate` ceiling (§7.4) — everything that
 * replaced the deleted `scheduledNotificationSweep`.
 *
 * There is no timer, no Cloud Scheduler job, no Cloud Tasks queue and no idle polling: both windows
 * are evaluated **lazily, on the next write**, which is the only moment there is anything to decide.
 *
 * ## No transaction, and that is a consequence of §7.3 rather than an oversight
 *
 * [bumpActivity] is a plain read followed by a merge write. Two concurrent writes to the same
 * aircraft do race, and every race is benign:
 *
 * - `FieldValue.increment` is applied server-side against the **stored** value, not the read one,
 *   so no count is lost. The read is used only for the two boolean decisions.
 * - Two writers both deciding "new session" stamp `firstWriteAt` milliseconds apart, so at worst one
 *   extra tray entry appears that nothing later updates.
 * - Two writers both deciding the throttle expired send twice under the same id and `collapse_key`,
 *   which the tray and FCM collapse into one.
 *
 * Duplicate sends collapse, so the counter does not need transactional exactness. A transaction
 * would buy nothing and add a retry loop on the hottest document in the feature.
 *
 * ## Why the stored count is a lifetime total and not the session count
 *
 * §7.4's pseudocode writes `changeCount = newSession ? 1 : increment(1)`. That has one case it gets
 * wrong, and it is the *first* write of a session — the moment two collaborators are most likely to
 * be typing at once. Both read no document, both decide "new session", and both write the literal
 * `1`. The second write does not increment anything; it overwrites. One collaborator's edit
 * disappears from the count, and no later write ever recovers it.
 *
 * So the document stores [NotificationActivityDoc.writeCount] — a lifetime total that is **only**
 * ever incremented, never assigned — plus `sessionBaseCount`, the total as it stood when the
 * session began. The session count the body reports is the difference. Ending a session now moves a
 * marker instead of resetting a counter, and there is no longer any write that can clobber another.
 *
 * ## The hot-document limit, stated plainly
 *
 * One document per `(aircraft, recordType, actor)` against Firestore's ~1 write/sec sustained
 * single-document rate. A stress-test import of 200 records by one actor is well past that: latency
 * climbs and some invocations get contention errors, which the trigger's own retry handles. Nothing
 * is lost — a dropped increment costs one unit of a count the next write corrects.
 * [AIRCRAFT_HOURLY_CEILING] does **not** rescue that case and is not claimed to (§7.4).
 */

export type ActivityBump = {
  /** Writes in the current working session — what the body reports. */
  changeCount: number;
  /** The session's `firstWriteAt`, which is what the notification id is keyed on (§7.3). */
  sessionStartMs: number;
  /** True when this write was counted but must not interrupt anyone yet (§7.4 step 5). */
  throttled: boolean;
  /** Tail number cached on a previous pass, if there is one. Cosmetic, so staleness is harmless. */
  cachedAircraftLabel: string | null;
};

export type BumpInput = {
  hostUid: string;
  aircraftId: string;
  recordType: RecordType;
  actorUid: string;
  nowMs: number;
};

/**
 * Counts one write and decides whether it should send.
 *
 * Counting always happens; only sending is throttled, so a suppressed write still advances the
 * session and the next send that does get through reports the true total.
 */
export async function bumpActivity(input: BumpInput): Promise<ActivityBump> {
  const { hostUid, aircraftId, recordType, actorUid, nowMs } = input;
  const ref = adminDb.doc(activityDocPath(hostUid, aircraftId, recordType, actorUid));
  const snap = await ref.get();
  const previous = snap.exists ? (snap.data() as Partial<NotificationActivityDoc>) : null;

  const lastWriteAtMs = toMillis(previous?.lastWriteAt);
  // A gap this long ends the working session, rolling BOTH the count and `firstWriteAt` — the
  // latter is what leaves a finished session's tray entry alone instead of overwriting it.
  const newSession = lastWriteAtMs == null || nowMs - lastWriteAtMs > ACTIVITY_WINDOW_MS;

  const previousWriteCount = previous?.writeCount ?? 0;
  const sessionBaseCount = newSession ? previousWriteCount : (previous?.sessionBaseCount ?? 0);
  const sessionStartMs = newSession ? nowMs : (toMillis(previous?.firstWriteAt) ?? nowMs);
  const changeCount = previousWriteCount + 1 - sessionBaseCount;

  await ref.set(
    {
      hostUid,
      aircraftId,
      recordType,
      actorUid,
      // Only ever incremented. See the "lifetime total" note above for why this is not assigned.
      writeCount: FieldValue.increment(1),
      firstWriteAt: Timestamp.fromMillis(sessionStartMs),
      lastWriteAt: Timestamp.fromMillis(nowMs),
      ...(newSession
        ? {
            sessionBaseCount,
            // A new session has its own tray entry to fill, so it must not be throttled against the
            // previous session's send. Clearing this is what lets the first write of a session post
            // immediately.
            lastSentAt: null,
          }
        : {}),
    },
    { merge: true },
  );

  const lastSentAtMs = newSession ? null : toMillis(previous?.lastSentAt);
  const throttled = lastSentAtMs != null && nowMs - lastSentAtMs < MIN_REPOST_INTERVAL_MS;

  return {
    changeCount,
    sessionStartMs,
    throttled,
    cachedAircraftLabel: previous?.aircraftLabel || null,
  };
}

/** The session count a document currently represents: the whole point of the two stored numbers. */
export function sessionChangeCount(doc: Partial<NotificationActivityDoc>): number {
  return (doc.writeCount ?? 0) - (doc.sessionBaseCount ?? 0);
}

/** Stamps a send onto the counter, so the next write throttles against it. */
export async function markActivitySent(
  input: BumpInput,
  labels: { aircraftLabel: string; actorDisplayName: string },
): Promise<void> {
  await adminDb
    .doc(activityDocPath(input.hostUid, input.aircraftId, input.recordType, input.actorUid))
    .set(
    {
      lastSentAt: Timestamp.fromMillis(input.nowMs),
      aircraftLabel: labels.aircraftLabel,
      // Re-read on every send rather than resolved once: this document lives for the life of the
      // share, so a name cached at first contact would never refresh, and the notification would
      // keep naming someone by a display name they have since changed. One extra read, on a path
      // the throttle already limits to ~2/min/key.
      actorDisplayName: labels.actorDisplayName,
    },
    { merge: true },
  );
}

export type RateState = {
  sendCount: number;
  /** True once the "a lot of activity" message has gone out for this hour, so it goes out once. */
  ceilingNotified: boolean;
};

/**
 * Reads the aircraft's hourly send budget. **A read, not a write** — this is checked on every write
 * and written only on a send, which is what keeps it from becoming a hotter document than the one it
 * protects.
 */
export async function readRateState(
  hostUid: string,
  aircraftId: string,
  nowMs: number,
): Promise<RateState> {
  const snap = await adminDb.doc(rateDocPath(hostUid, aircraftId, nowMs)).get();
  if (!snap.exists) return { sendCount: 0, ceilingNotified: false };
  const doc = snap.data() as Partial<NotificationRateDoc> & { ceilingNotified?: boolean };
  return {
    sendCount: doc.sendCount ?? 0,
    ceilingNotified: doc.ceilingNotified === true,
  };
}

export function ceilingTripped(rate: RateState): boolean {
  return rate.sendCount >= AIRCRAFT_HOURLY_CEILING;
}

/** Increments the hour's send budget, and optionally marks the ceiling message as delivered. */
export async function recordSend(
  hostUid: string,
  aircraftId: string,
  nowMs: number,
  options: { ceilingNotified?: boolean } = {},
): Promise<void> {
  await adminDb.doc(rateDocPath(hostUid, aircraftId, nowMs)).set(
    {
      sendCount: FieldValue.increment(1),
      // These buckets are worthless the hour after they are written. Configure a Firestore TTL
      // policy on this field rather than growing a collection nothing ever reads again.
      expireAt: Timestamp.fromMillis(nowMs + RATE_DOC_TTL_MS),
      ...(options.ceilingNotified === true ? { ceilingNotified: true } : {}),
    },
    { merge: true },
  );
}

/** Two hours: long enough that the bucket outlives its own window with room for clock skew. */
const RATE_DOC_TTL_MS = 2 * 60 * 60 * 1000;

function toMillis(value: unknown): number | null {
  if (value == null) return null;
  if (value instanceof Timestamp) return value.toMillis();
  if (typeof value === "number") return value;
  return null;
}
