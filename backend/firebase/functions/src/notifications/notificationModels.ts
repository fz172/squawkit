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

// --- Notification ids (§7.3, §7.5) -------------------------------------------------------------

/**
 * `n1:{aircraftId}:{recordType}:{recordId}:{atMs}` — one id per write (design decision,
 * 2026-08-27: coalescing removed). Every write fans out its own concrete notification naming the
 * record and what happened to it, and nothing here ever collapses one tray entry onto another; the
 * timestamp only keeps two rapid writes to the same record from racing onto an identical id.
 *
 * The earlier design (§7.3, now historical) kept one counter shared by every recipient, per
 * `(aircraft, recordType, actor)`, and replaced the tray entry in place, summarizing as "made N
 * changes." That lost the specific record a pilot had already looked at the moment a second,
 * unrelated write replaced it with a bigger, vaguer number — worse than helpful once someone had
 * genuinely acted on an earlier notification, and worse again after a session boundary made the
 * count restart from a record nobody remembered opening. Concrete, one-per-write, never collapsed,
 * is what replaced it.
 */
export function activityNotificationId(
  aircraftId: string,
  recordType: RecordType,
  recordId: string,
  atMs: number,
): string {
  return `n1:${aircraftId}:${recordType}:${recordId}:${atMs}`;
}

/**
 * `n1esc:{aircraftId}:{squawkId}` — an escalation's own id, never the activity id (§7.5).
 *
 * That distinction is the whole rule: folding an escalation into the activity id would let the next
 * routine edit's push overwrite "Sarah raised Left brake dragging to AOG" with a mundane update
 * notice, silently replacing a grounding alert. It carries no actor, so nothing can collapse onto it
 * either.
 */
export function escalationNotificationId(aircraftId: string, squawkId: string): string {
  return `n1esc:${aircraftId}:${squawkId}`;
}
