import {
  aircraftTabForRecordType,
  activityNotificationId,
  escalationNotificationId,
  highVolumeNotificationId,
  type RecordType,
} from "./notificationModels.js";

/**
 * The N1 wire contract: what the server puts in a data-only FCM message, and what the client is
 * expected to do with it (§7.6).
 *
 * **Data-only, never a notification-type message.** A notification-type message is displayed by the
 * OS itself on Android when the app is backgrounded, which would bypass both the per-channel
 * routing `LocalNotifier` sets up and the `NotificationTapRouter` that makes a tap land on the right
 * record. The client builds the visible notification from `data` and posts it under
 * [PushData.notificationId], which is what makes §7.3's tray replacement happen on the device.
 *
 * ## Why keys and named values rather than rendered strings
 *
 * The server does not know the recipient's locale and the client already has `strings.xml`. So the
 * message names a string resource and supplies the *variable* values by name:
 *
 * | key | meaning |
 * |:--|:--|
 * | `titleKey` / `bodyKey` | `strings.xml` resource names in `feature/notifications/sharedassets` |
 * | `tailNumber` | `%1$s` of every title and body here |
 * | `actorName` | the collaborator's display name; **empty** means fall back to `notification_n1_actor_fallback` |
 * | `changeCount` | writes in the session, decimal — the plural body's `%2$d` |
 * | `recordType` | `squawk` \| `task` \| `log` \| `aircraft`; the client maps it to the section labels |
 * | `recordTitle` | escalation only — the squawk's own title |
 * | `fromRank` / `toRank` | escalation only — `UrgencyRank` values, mapped to `squawk_priority_label_*` |
 * | `recipientUid` | who this copy is for; the client drops it if that is not who is signed in |
 *
 * ## Why `recipientUid` is stamped at send time and is not a field of [PushData]
 *
 * One [PushData] addresses a whole fan-out — every recipient of one aircraft's activity gets the
 * same text. The *address* is the one thing that differs per device, so [toDataMap] takes it as a
 * second argument and `sendPush` supplies it per recipient group. Putting it in [PushData] would
 * mean rebuilding the message once per recipient for a field none of the builders can know.
 *
 * A client older than this field simply ignores it; a client newer than a server that does not send
 * it must keep rendering, so an absent value means "not addressed" rather than "addressed to
 * nobody" (issue P4.13).
 *
 * §7.6 sketched this as `titleKey`/`bodyArgs`, a positional array. Named values are used instead
 * because a positional array cannot work here: `notification_n1_title` interpolates a **localized
 * section label** ("Squawks") that the server cannot render, and `..._body_single` and
 * `..._body_plural` do not share an argument order. Sending the raw values and letting the client
 * assemble them is the only form that survives both facts.
 */
export type PushData = {
  /** `collaboration` (an activity summary) or `urgency` (a §7.5 escalation). */
  class: "collaboration" | "urgency";
  /** `NotificationChannel` name — COLLABORATION or URGENCY. */
  channel: "COLLABORATION" | "URGENCY";
  notificationId: string;
  highPriority: "true" | "false";
  aircraftId: string;
  recordType: RecordType;
  tapTarget: string;
  titleKey: string;
  bodyKey: string;
  tailNumber: string;
  actorName: string;
  changeCount?: string;
  recordId?: string;
  recordTitle?: string;
};

/**
 * How long FCM should keep trying. An escalation matters a day later; an activity summary a day
 * later is mild noise that replaces in the tray anyway. One value keeps the contract simple, and
 * `collapse_key` already ensures a device offline for a whole burst wakes to one message, not
 * eight.
 */
export const PUSH_TTL_SECONDS = 24 * 60 * 60;

export type ActivityMessageInput = {
  aircraftId: string;
  recordType: RecordType;
  actorUid: string;
  actorName: string;
  tailNumber: string;
  changeCount: number;
  sessionSeq: number;
};

/** The §7.3 activity summary — "Dave Chen made 5 changes to tasks", replacing in place. */
export function activityPushData(input: ActivityMessageInput): PushData {
  const notificationId = activityNotificationId(
    input.aircraftId,
    input.recordType,
    input.actorUid,
    input.sessionSeq,
  );
  return {
    class: "collaboration",
    channel: "COLLABORATION",
    notificationId,
    // Collaboration activity is never high priority — that is what N2's urgency tiers are for, and
    // §7.3 is explicit that an activity summary must never replace a grounding alert.
    highPriority: "false",
    aircraftId: input.aircraftId,
    recordType: input.recordType,
    tapTarget: `aircraft:${input.aircraftId}:${aircraftTabForRecordType(input.recordType)}`,
    titleKey: "notification_n1_title",
    bodyKey:
      input.changeCount === 1 ? "notification_n1_body_single" : "notification_n1_body_plural",
    tailNumber: input.tailNumber,
    actorName: input.actorName,
    changeCount: String(input.changeCount),
  };
}

export type EscalationMessageInput = {
  aircraftId: string;
  squawkId: string;
  title: string;
  kind: "created" | "raised";
  tailNumber: string;
  actorName: string;
};

/**
 * The §7.5 bypass, under `n1esc:{aircraftId}:{squawkId}` so no later routine edit can replace an
 * escalation alert with a shrug.
 *
 * **The bodies are N1's own, not the N2 ones §7.5 originally reused.** Both can fire for the same
 * squawk — this within seconds of the write, N2 at the recipient's next scan — and they are no
 * longer deduplicated, because they are no longer saying the same thing. Only the server knows the
 * actor, so only this notification can name them; word-for-word identical copy is exactly what would
 * make the second arrival read as a duplicate rather than as news.
 *
 * `created` keeps its own title: "Priority raised" would contradict a body saying the squawk was
 * just created — including at AOG, which is no longer a separate headline (design decision,
 * 2026-08-26: AOG reports exactly like any other priority raise, not as its own tier).
 */
export function escalationPushData(input: EscalationMessageInput): PushData {
  const created = input.kind === "created";
  return {
    class: "urgency",
    channel: "URGENCY",
    notificationId: escalationNotificationId(input.aircraftId, input.squawkId),
    highPriority: "false",
    aircraftId: input.aircraftId,
    recordType: "squawk",
    tapTarget: `squawk:${input.aircraftId}:${input.squawkId}`,
    titleKey: created ? "notification_n1_title_squawk_created" : "notification_title_priority_raised",
    bodyKey: created
      ? "notification_n1_body_squawk_created"
      : "notification_n1_body_squawk_raised",
    tailNumber: input.tailNumber,
    actorName: input.actorName,
    recordId: input.squawkId,
    recordTitle: input.title,
  };
}

/**
 * The single "N4589T · a lot of activity" message sent when the per-aircraft hourly ceiling trips
 * (§7.4), after which the fan-out goes quiet for the rest of the hour.
 *
 * It reports a *volume*, not a change, so it carries no actor, no count and no record — and it taps
 * through to the aircraft overview, because there is no one record that explains it.
 */
export function highVolumePushData(
  aircraftId: string,
  tailNumber: string,
  atMs: number,
): PushData {
  return {
    class: "collaboration",
    channel: "COLLABORATION",
    notificationId: highVolumeNotificationId(aircraftId, atMs),
    highPriority: "false",
    aircraftId,
    recordType: "aircraft",
    tapTarget: `aircraft:${aircraftId}:overview`,
    titleKey: "notification_n1_title_high_volume",
    bodyKey: "notification_n1_body_high_volume",
    tailNumber,
    actorName: "",
  };
}

/**
 * FCM `data` values must be strings, so absent optional fields are dropped rather than sent empty.
 *
 * [recipientUid] is added here rather than carried on [PushData] — see the note on the type. It is
 * the last thing written, so a builder can never shadow it.
 */
export function toDataMap(data: PushData, recipientUid: string): Record<string, string> {
  return {
    ...(Object.fromEntries(
      Object.entries(data).filter(([, value]) => value !== undefined),
    ) as Record<string, string>),
    recipientUid,
  };
}
