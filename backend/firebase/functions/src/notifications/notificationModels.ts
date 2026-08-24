import type { Timestamp } from "firebase-admin/firestore";

/**
 * Collection names, document paths, tuning constants and notification-id builders for N1
 * collaboration push (docs/notifications/notifications_design.md §7).
 *
 * This module is the single source of truth the trigger, the sender and the emulator suite all
 * consume, the same way `sharingModels.ts` is for the share ACL — so a path or a window never
 * drifts between the code that writes a document and the test that asserts on it. Types and pure
 * functions only; no Firestore access.
 */

// --- Device token registry (§7.1) --------------------------------------------------------------

/**
 * `users/{uid}/push_devices/{installationId}` — plain fields, **not** proto bytes, because the
 * server must read them. Same rationale as the sharing ACL exception (`sharingModels.ts`).
 *
 * Written only by the client's `PushTokenRegistrar`; the existing `users/{userId}/{document=**}`
 * own-tree rule already grants exactly that and nothing wider, so no rules change was needed.
 */
export const PUSH_DEVICES_SUBCOLLECTION = "push_devices";

export type PushPlatform = "android" | "ios" | "web";

export type PushDeviceDoc = {
  /** FCM registration token. Rotates; the document id (the install id) does not. */
  token: string;
  platform: PushPlatform;
  appVersion: string;
  /** Per-device silence switch — the one preference that is deliberately NOT synced (§4, Q2). */
  enabled: boolean;
  updatedAt: Timestamp;
};

export function pushDevicesCollectionPath(uid: string): string {
  return `users/${uid}/${PUSH_DEVICES_SUBCOLLECTION}`;
}

export function pushDeviceDocPath(uid: string, installationId: string): string {
  return `${pushDevicesCollectionPath(uid)}/${installationId}`;
}

// --- Recipient preferences ---------------------------------------------------------------------

/**
 * `users/{uid}/notification_settings/main` — a synced entity, so an opaque `SyncDocWire` whose
 * payload decodes to `settings.NotificationSettings`. The doc id matches
 * `NotificationPrefsManagerImpl.DOC_ID`; there is exactly one per account.
 */
export const NOTIFICATION_SETTINGS_COLLECTION = "notification_settings";
export const NOTIFICATION_SETTINGS_DOC_ID = "main";

export function notificationSettingsDocPath(uid: string): string {
  return `users/${uid}/${NOTIFICATION_SETTINGS_COLLECTION}/${NOTIFICATION_SETTINGS_DOC_ID}`;
}

// --- Record types (§7.2) -----------------------------------------------------------------------

/**
 * The four classes of collaboration activity, one per toggle in `notification_settings.proto`.
 *
 * These are the wire values the notification id and the FCM payload carry, and they are **not** the
 * Firestore path segments: `maintenance_task` is `task` here, matching `WebForeignWriteDetector`'s
 * `RecordType.wire` so a tray entry posted by web and one posted from push share an id.
 */
export const RECORD_TYPE = {
  AIRCRAFT: "aircraft",
  SQUAWK: "squawk",
  TASK: "task",
  LOG: "log",
} as const;

export type RecordType = (typeof RECORD_TYPE)[keyof typeof RECORD_TYPE];

/**
 * The `{kind}` path segment → the record type it notifies as, or `null` for a kind that is not
 * collaboration activity.
 *
 * `maintenance_overview` is deliberately absent. No toggle in `notification_settings.proto` covers
 * it, so notifying about it would be **unmutable** — and it is derived bookkeeping (accumulated
 * hours) rather than something a collaborator authored on purpose.
 */
export function recordTypeForKind(kind: string): RecordType | null {
  switch (kind) {
    case "squawk":
      return RECORD_TYPE.SQUAWK;
    case "maintenance_task":
      return RECORD_TYPE.TASK;
    case "maintenance_log":
      return RECORD_TYPE.LOG;
    default:
      return null;
  }
}

/** The shell section a tap on this record type should land on (`NotificationTapTarget.Aircraft`). */
export function aircraftTabForRecordType(recordType: RecordType): string {
  switch (recordType) {
    case RECORD_TYPE.SQUAWK:
      return "squawks";
    case RECORD_TYPE.TASK:
      return "tasks";
    case RECORD_TYPE.LOG:
      return "logs";
    case RECORD_TYPE.AIRCRAFT:
      return "overview";
  }
}

// --- The activity counter and the rate ceiling (§7.4) ------------------------------------------

export const NOTIFICATION_ACTIVITY_COLLECTION = "notification_activity";
export const NOTIFICATION_RATE_COLLECTION = "notification_rate";

/**
 * One document per `(host, aircraft, recordType, actor)`.
 *
 * **[hostUid] leads the key, and it is not decoration (#204).** An aircraft id is unique only
 * *within one user's tree* — it is a 20-char client-generated string, and the own-tree rule lets any
 * account create `users/{self}/aircraft/{anyId}`. Keyed on the aircraft id alone, these documents
 * live in one global namespace that any account can reach into by choosing an id it has seen. Keyed
 * under the host — which comes from the trigger *path* and so cannot be claimed — a document a
 * writer can influence only ever governs that writer's own tree. Exactly the property
 * `aircraftShareDocPath` was re-keyed for.
 *
 * Ids concatenate with `__`: Firebase uids and aircraft ids are alphanumeric (`IdGenerator.kt`) and
 * `recordType` is a fixed enum, so the separator is unambiguous. The leading uid also keeps the id
 * clear of Firestore's reserved `__.*__` form.
 */
export function activityDocPath(
  hostUid: string,
  aircraftId: string,
  recordType: RecordType,
  actorUid: string,
): string {
  return `${NOTIFICATION_ACTIVITY_COLLECTION}/${hostUid}__${aircraftId}__${recordType}__${actorUid}`;
}

export type NotificationActivityDoc = {
  hostUid: string;
  aircraftId: string;
  recordType: RecordType;
  actorUid: string;
  /** Tail number, resolved once. Cosmetic, so staleness is harmless. */
  aircraftLabel: string;
  /** From `aircraft_shares/{host}/aircraft/{ac}/members/{actor}.displayName`. May be empty. */
  actorDisplayName: string;
  /** Session start — **part of the notification id** (§7.3), not telemetry. */
  firstWriteAt: Timestamp;
  lastWriteAt: Timestamp;
  /**
   * Lifetime writes on this key. **Only ever incremented, never assigned** — see the "lifetime
   * total" note in `activityCounter.ts` for the concurrent cold start that assigning loses.
   */
  writeCount: number;
  /** [writeCount] as it stood when the current session began. The body reports the difference. */
  sessionBaseCount: number;
  lastSentAt: Timestamp | null;
};

/**
 * One document per aircraft per UTC hour. Read on every write, written only on a send.
 *
 * Namespaced under [hostUid] for the reason [activityDocPath] gives, and this is the key where it
 * bites hardest: the aircraft id is the *only* other component, so without the host, any account
 * that knows an aircraft id — every current and former member of that share — could burn a victim
 * aircraft's hourly budget from its own tree and silence that share's notifications for the hour.
 */
export function rateDocPath(hostUid: string, aircraftId: string, atMs: number): string {
  return `${NOTIFICATION_RATE_COLLECTION}/${hostUid}__${aircraftId}__${rateWindowKey(atMs)}`;
}

/** `yyyymmddHH`, UTC. The hour is a bucket boundary, not a local-time claim about anyone's day. */
export function rateWindowKey(atMs: number): string {
  const at = new Date(atMs);
  const pad = (n: number, width = 2) => String(n).padStart(width, "0");
  return (
    pad(at.getUTCFullYear(), 4) +
    pad(at.getUTCMonth() + 1) +
    pad(at.getUTCDate()) +
    pad(at.getUTCHours())
  );
}

export type NotificationRateDoc = {
  sendCount: number;
  /** For a Firestore TTL policy — these buckets are worthless the hour after they are written. */
  expireAt: Timestamp;
};

/**
 * A gap this long ends the working session, resetting `changeCount` **and** `firstWriteAt` — the
 * latter is what rolls the notification id so a finished session's tray entry is left alone rather
 * than overwritten by the next session's first edit (§7.3).
 *
 * Kept in step with `ActivityCounter.ACTIVITY_WINDOW` on the client, which is web's in-memory
 * stand-in for this document.
 */
export const ACTIVITY_WINDOW_MS = 30 * 60 * 1000;

/**
 * The per-key storm guard: a bulk import writing 200 records sends at most twice a minute per key
 * instead of 200 times. The cost is a count that lags by up to 30 seconds; the next write corrects
 * it, and the last write of a burst is the one that matters.
 */
export const MIN_REPOST_INTERVAL_MS = 30 * 1000;

/**
 * Sends per aircraft per hour, across every key and every recipient, before the fan-out stops.
 *
 * Bounds the **aircraft**, not the key (PRD §9.4): `feature/stresstest` is compiled into every
 * build and will be pointed at a shared aircraft. Sixty is roughly four record types × two active
 * collaborators × the ~2/min/key the throttle already permits, i.e. comfortably above what real
 * collaboration produces and far below what a runaway loop does.
 *
 * It bounds sustained abuse over minutes and hours. It does **not** bound one fast burst — sends
 * are already throttled to ~2/min/key, so a ten-second burst never accumulates enough *sends* to
 * trip an hourly *send* ceiling. §7.4 says so plainly and this constant does not pretend otherwise.
 */
export const AIRCRAFT_HOURLY_CEILING = 60;

// --- Notification ids (§7.3, §7.5) -------------------------------------------------------------

/**
 * `n1:{aircraftId}:{recordType}:{actorUid}:{sessionStart}` — PRD §5.4's coalescing key, moved from
 * a server buffer into the notification id.
 *
 * Byte-identical to the tag `WebForeignWriteDetector` posts under, on purpose: the same event seen
 * by an open web tab and by a phone must not become two different tray entries.
 */
export function activityNotificationId(
  aircraftId: string,
  recordType: RecordType,
  actorUid: string,
  sessionStartMs: number,
): string {
  return `n1:${aircraftId}:${recordType}:${actorUid}:${sessionStartMs}`;
}

/**
 * `n1esc:{aircraftId}:{squawkId}` — an escalation's own id, never the activity id (§7.5).
 *
 * That distinction is the whole rule: folding an escalation into the activity id would let the next
 * routine edit overwrite "Sarah raised Left brake dragging to AOG" with "Sarah made 4 changes to
 * squawks", silently replacing a grounding alert with a shrug. It carries no actor and no session,
 * so nothing can collapse onto it either.
 */
export function escalationNotificationId(aircraftId: string, squawkId: string): string {
  return `n1esc:${aircraftId}:${squawkId}`;
}

/**
 * `n1max:{aircraftId}:{yyyymmddHH}` — the one "a lot of activity" message sent when
 * [AIRCRAFT_HOURLY_CEILING] trips, keyed to the hour it trips in.
 *
 * Keyed by hour rather than being a single id per aircraft so that a second storm tomorrow is its
 * own tray entry instead of silently replacing a notice the pilot may never have read — the same
 * rule `sessionStart` encodes for the activity id.
 */
export function highVolumeNotificationId(aircraftId: string, atMs: number): string {
  return `n1max:${aircraftId}:${rateWindowKey(atMs)}`;
}
