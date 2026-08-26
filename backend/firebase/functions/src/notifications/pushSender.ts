import { getMessaging, type MulticastMessage } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";

import { adminDb } from "../config/firebaseAdmin.js";
import {
  pushDeviceDocPath,
  pushDevicesCollectionPath,
  type PushDeviceDoc,
} from "./notificationModels.js";
import { PUSH_TTL_SECONDS, toDataMap, type PushData } from "./pushMessages.js";

/** One device that should receive a message: which account it belongs to, and how to reach it. */
export type PushTarget = {
  uid: string;
  installationId: string;
  token: string;
};

/**
 * Every device of [uid] that has push switched on.
 *
 * `enabled` is the per-device silence switch (Q2) — the one preference that is deliberately not
 * synced, because "quiet on the iPad" is a statement about the iPad. It is read as `!== false` so a
 * document written before the field existed still receives.
 */
export async function enabledTokensFor(uid: string): Promise<PushTarget[]> {
  const snap = await adminDb.collection(pushDevicesCollectionPath(uid)).get();
  const seen = new Set<string>();
  const targets: PushTarget[] = [];
  for (const doc of snap.docs) {
    const device = doc.data() as PushDeviceDoc;
    if (device.enabled === false) continue;
    const token = device.token;
    if (typeof token !== "string" || token.length === 0) continue;
    // A token can outlive its install id (a reinstall that restores a backup), so the same string
    // can appear twice. Sending it twice would put two identical entries through FCM for one device.
    if (seen.has(token)) continue;
    seen.add(token);
    targets.push({ uid, installationId: doc.id, token });
  }
  return targets;
}

/**
 * Sends one data-only message to every target, and returns how many FCM accepted.
 *
 * **All three of `notificationId`, `android.collapseKey` and `apns-collapse-id` carry the same
 * value** (§7.6). The id makes the *tray* replace on the device; the two collapse headers make the
 * *transport* do the same thing for a device that was offline for the whole burst, so it receives
 * only the last message rather than eight.
 *
 * Failures are logged, never thrown. A trigger that threw here would be retried by Firestore, and a
 * retry re-sends to every device that already succeeded — the one failure mode replacement-based
 * coalescing does not paper over, since a retry a minute later can land under a rolled session id.
 *
 * Known FCM limit, not worked around here: a device holds **at most four distinct collapse keys**
 * at once, and past that FCM picks which ones to keep. A key is one (aircraft, record type, actor,
 * session), so a pilot who is offline while five separate streams of activity run would have one of
 * them collapsed unpredictably. Only the *undelivered* backlog is affected — the tray replacement
 * on a connected device is keyed on `notificationId` and has no such limit — and reaching it needs
 * five concurrent working sessions on one fleet.
 */
export async function sendPush(targets: PushTarget[], data: PushData): Promise<number> {
  if (targets.length === 0) return 0;

  // One multicast per recipient rather than one for the whole fan-out, because each copy is stamped
  // with the uid it is addressed to (issue P4.13) and that is the one field that differs per device.
  // This is not the extra network cost it looks like: `sendEachForMulticast` already issues one FCM
  // request per token internally, so the same number of requests goes out either way.
  const byRecipient = new Map<string, PushTarget[]>();
  for (const target of targets) {
    const group = byRecipient.get(target.uid);
    if (group == null) byRecipient.set(target.uid, [target]);
    else group.push(target);
  }

  const results = await Promise.all(
    [...byRecipient].map(([uid, group]) => sendToRecipient(uid, group, data)),
  );

  // Pruned once for the whole fan-out, so a stale token on two accounts logs one line, not two.
  await pruneDeadTokens(results.flatMap((result) => result.dead));
  return results.reduce((total, result) => total + result.sent, 0);
}

/** One recipient's devices, addressed to them. Never throws — a failure here costs only this uid. */
async function sendToRecipient(
  recipientUid: string,
  targets: PushTarget[],
  data: PushData,
): Promise<{ sent: number; dead: PushTarget[] }> {
  const message: MulticastMessage = {
    tokens: targets.map((t) => t.token),
    data: toDataMap(data, recipientUid),
    android: {
      // Data-only: the client renders it. A notification-type message would be drawn by the OS when
      // the app is backgrounded, bypassing the per-channel routing and the tap router (§7.6).
      collapseKey: data.notificationId,
      // A normal-priority data message can sit in Doze for hours, which would miss PRD §4's
      // "~a minute" outright. The per-key throttle and the hourly ceiling are what keep the volume
      // low enough for this to be honest.
      priority: "high",
      ttl: PUSH_TTL_SECONDS * 1000,
    },
    apns: {
      headers: {
        "apns-collapse-id": data.notificationId,
        // A data-only APNs message is a background push; P5 adds the notification service
        // extension that lets iOS render one while backgrounded (§7.6). Until then an iOS device
        // has no registered token at all, so nothing here can reach one.
        "apns-push-type": "background",
        "apns-priority": "5",
        "apns-expiration": String(Math.floor(Date.now() / 1000) + PUSH_TTL_SECONDS),
      },
      payload: { aps: { contentAvailable: true } },
    },
  };

  let response;
  try {
    response = await getMessaging().sendEachForMulticast(message);
  } catch (e) {
    logger.error("FCM multicast failed", { notificationId: data.notificationId, error: String(e) });
    return { sent: 0, dead: [] };
  }

  const dead: PushTarget[] = [];
  response.responses.forEach((result, index) => {
    if (result.success) return;
    const target = targets[index];
    const code = (result.error as { code?: string } | undefined)?.code ?? "";
    if (DEAD_TOKEN_CODES.has(code)) {
      dead.push(target);
      return;
    }
    logger.warn("FCM send failed", {
      uid: target.uid,
      installationId: target.installationId,
      code,
      notificationId: data.notificationId,
    });
  });

  return { sent: response.successCount, dead };
}

/**
 * FCM's way of saying the app is gone from that device — uninstalled, or the token superseded.
 *
 * Left in place, a dead token is retried on every send forever. Deleting it is also the privacy
 * answer: the registry should not carry a record of a device that no longer runs the app.
 */
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

async function pruneDeadTokens(dead: PushTarget[]): Promise<void> {
  if (dead.length === 0) return;
  await Promise.all(
    dead.map(async (target) => {
      try {
        await adminDb.doc(pushDeviceDocPath(target.uid, target.installationId)).delete();
      } catch (e) {
        logger.warn("Could not prune a dead push token", {
          uid: target.uid,
          installationId: target.installationId,
          error: String(e),
        });
      }
    }),
  );
  logger.info("Pruned dead push tokens", { count: dead.length });
}
