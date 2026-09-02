import { logger } from "firebase-functions/v2";

import { adminDb } from "../config/firebaseAdmin.js";
import {
  thingShareDocPath,
  shareMemberDocPath,
  type ThingShareDoc,
  type ShareMemberDoc,
} from "../sharing/sharingModels.js";
import { NotificationSettings } from "../generated/proto/settings/notification_settings.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";
import { notificationSettingsDocPath } from "./notificationModels.js";

/**
 * Who should hear about a write, and whether they have asked to.
 *
 * Everything here is re-read **on every send** (§7.4 step 6). With nothing buffered there is no
 * cached audience that could outlive a revocation, so PRD §9.5 — "a revoked member must not receive
 * the notification" — stops being a rule to enforce and becomes a property of the shape.
 */

export type ShareAudience = {
  /** Every uid on the ACL, including the host. */
  memberUids: string[];
};

/**
 * The ACL for a shared aircraft, or `null` when there is no audience.
 *
 * The `<= 1` test rather than `!exists` is deliberate: it also covers a share whose last member
 * left but whose ACL document survives. Reading this document is the **early exit that keeps the
 * whole feature cheap** — most writes are on unshared aircraft and cost exactly one read.
 *
 * [hostUid] always comes from the trigger path, never from a field. That is the same property
 * `firestore.rules` leans on (#204) and it is what makes the audience unspoofable.
 */
export async function readShareAudience(
  hostUid: string,
  thingId: string,
): Promise<ShareAudience | null> {
  const snap = await adminDb.doc(thingShareDocPath(hostUid, thingId)).get();
  if (!snap.exists) return null;
  const share = snap.data() as ThingShareDoc;
  if (share.hostUid !== hostUid) return null; // someone else's aircraft that merely shares the id
  const memberUids = Object.keys(share.memberRoles ?? {});
  if (memberUids.length <= 1) return null; // unshared, or a share nobody is left in
  return { memberUids };
}

/**
 * The actor's display name, or `""` when the roster has nothing to offer.
 *
 * Empty is passed through to the client rather than substituted here: the fallback text
 * ("A collaborator") is a localized string the server cannot render.
 */
export async function readActorDisplayName(
  hostUid: string,
  thingId: string,
  actorUid: string,
): Promise<string> {
  try {
    const snap = await adminDb.doc(shareMemberDocPath(hostUid, thingId, actorUid)).get();
    if (!snap.exists) return "";
    return (snap.data() as ShareMemberDoc).displayName ?? "";
  } catch (e) {
    logger.warn("Could not read an actor display name", { hostUid, thingId, actorUid, error: String(e) });
    return "";
  }
}

/**
 * A recipient's notification preferences, defaulting to all-on.
 *
 * **Every field in the proto is inverted (`*_disabled`)**, precisely so that an absent document —
 * a user who has never opened the settings screen — decodes to all-false and therefore all-on. So
 * "could not read it" and "has no document" land on the same, correct answer, and a preferences
 * read that fails can never silence someone.
 */
export async function readNotificationSettings(uid: string): Promise<NotificationSettings> {
  try {
    const snap = await adminDb.doc(notificationSettingsDocPath(uid)).get();
    if (!snap.exists) return NotificationSettings.create();
    const doc = snap.data() as SyncDocWire;
    if (doc.deleted === true) return NotificationSettings.create();
    const bytes = payloadBytes(doc.payload);
    if (bytes == null) return NotificationSettings.create();
    return NotificationSettings.decode(bytes);
  } catch (e) {
    logger.warn("Could not read notification settings; treating as defaults", { uid, error: String(e) });
    return NotificationSettings.create();
  }
}

/**
 * Does this recipient want collaboration activity — "Dave Chen made 5 changes to tasks"? One toggle
 * now covers aircraft/squawk/task/log records alike (design decision, 2026-08-26); `recordType` no
 * longer distinguishes anything here, but callers still pass it for the push payload's own content.
 */
export function honorsActivity(settings: NotificationSettings): boolean {
  if (settings.allDisabled) return false;
  return !settings.collaborationDisabled;
}

/**
 * Does this recipient want a §7.5 escalation?
 *
 * Gated on the **priority/due** toggle, not the collaboration one, because what the recipient sees
 * is an urgency notification: N2's title, N2's body, N2's channel — including at AOG, which is not
 * its own tier and has no toggle of its own (design decision, 2026-08-26). Someone who muted
 * collaboration activity has not asked to stop hearing that a squawk's priority got worse.
 */
export function honorsEscalation(settings: NotificationSettings): boolean {
  if (settings.allDisabled) return false;
  return !settings.priorityDueDisabled;
}
