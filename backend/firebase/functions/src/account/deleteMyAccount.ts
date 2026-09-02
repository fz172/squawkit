import { getAuth } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminDb, adminStorage } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import {
  decodeSharedAircraftRef,
  sharedAircraftRefTombstone,
} from "../sharing/sharedAircraftRefWire.js";
import {
  THING_SHARES_COLLECTION,
  SHARE_THING_SUBCOLLECTION,
  thingShareDocPath,
  shareMemberDocPath,
  type ThingShareDoc,
} from "../sharing/sharingModels.js";

/**
 * Deletes the caller's account and everything belonging to it (#418).
 *
 * Required by App Store Review Guideline 5.1.1(v): an app that offers account creation must let a
 * user delete that account from inside the app. It applies to every account, not only Apple ones.
 *
 * **Server-side, and callable rather than client-driven,** for two reasons a client cannot satisfy:
 * a member's departure has to be written into *other* users' trees (the host's ACL), which rules
 * rightly forbid; and `users/{uid}` has to be removed recursively, which needs the Admin SDK.
 *
 * ## Order is load-bearing
 *
 * The Auth user is deleted **last**. Every step before it is idempotent, so a failure part-way
 * leaves an account that can still sign in and retry. Deleting the credential first would strand
 * whatever remained with no way to ever reach it again — unrecoverable, and invisible.
 *
 * ## Shared aircraft
 *
 * Shares this user hosts are torn down and every member loses access (#418). That matches what
 * already happens when a host deletes an aircraft (`onThingDeleted`): the data lives in the
 * host's tree, so it cannot outlive the host. Members are tombstoned rather than silently dropped,
 * which is what tells their devices to purge the local copy.
 *
 * ## Push tokens
 *
 * `users/{uid}/push_devices` goes with the rest of the tree in the `recursiveDelete` below — no
 * separate step, because that call already takes every subcollection. It is named here because it
 * is a **requirement**, not an incidental (notifications_design.md §12.3): a surviving token keeps
 * a deleted account's device receiving notifications about aircraft it no longer has any claim on,
 * and there would be no account left to switch them off from. `delete-account.test.ts` asserts it
 * rather than trusting the reading.
 */
export const deleteMyAccount = onCall<void, Promise<{ ok: true }>>(
  { region: FUNCTION_REGION, enforceAppCheck: true },
  async (request) => {
    const { uid } = requireAuthenticatedApp(request);

    // Belt and braces with the client, which does not offer the control to guests. A guest has no
    // cloud account to delete — everything is on-device — so this would be a no-op that
    // nevertheless destroyed their anonymous credential and with it their local data's owner.
    if (request.auth?.token?.firebase?.sign_in_provider === "anonymous") {
      throw new HttpsError("failed-precondition", "Guest sessions have no account to delete.");
    }

    logger.info("Account deletion requested", { uid });

    await tearDownHostedShares(uid);
    await leaveJoinedShares(uid);
    await deleteBlobs(uid);
    await adminDb.recursiveDelete(adminDb.doc(`users/${uid}`));
    await adminDb.doc(`subscriptions/${uid}`).delete();
    await getAuth().deleteUser(uid);

    logger.info("Account deleted", { uid });
    return { ok: true };
  },
);

/**
 * Every share this user hosts: tombstone the members' refs, then drop the ACL tree.
 *
 * The tombstone must land before the ACL goes — it is written into the member's own tree, and once
 * `memberRoles` is gone the rules no longer authorize anything about that share.
 */
async function tearDownHostedShares(uid: string): Promise<void> {
  const hosted = await adminDb
    .collection(THING_SHARES_COLLECTION)
    .doc(uid)
    .collection(SHARE_THING_SUBCOLLECTION)
    .get();

  for (const shareDoc of hosted.docs) {
    const share = shareDoc.data() as ThingShareDoc;
    if (share.hostUid !== uid) continue; // not ours to tear down; see onThingDeleted's note
    await Promise.all(
      Object.keys(share.memberRoles ?? {})
        .filter((memberUid) => memberUid !== uid) // the host holds the data directly, so has no ref
        .map((memberUid) =>
          adminDb
            .doc(`users/${memberUid}/shared_aircraft_ref/${shareDoc.id}`)
            .set(sharedAircraftRefTombstone()),
        ),
    );
  }

  await adminDb.recursiveDelete(adminDb.collection(THING_SHARES_COLLECTION).doc(uid));
}

/**
 * Every share this user merely belongs to: remove them from the host's ACL.
 *
 * Skipped by the `users/{uid}` delete below, because the membership record lives in the *host's*
 * tree. Left behind, the host would keep showing a member who no longer exists, and the deleted
 * uid would stay in `memberRoles` — readable by every other member of that aircraft.
 *
 * Failures are logged and stepped over rather than thrown: one unreachable host must not block the
 * user's own deletion, which is the part the guideline actually requires.
 */
async function leaveJoinedShares(uid: string): Promise<void> {
  const refs = await adminDb.collection(`users/${uid}/shared_aircraft_ref`).get();

  for (const refDoc of refs.docs) {
    const ref = decodeSharedAircraftRef(refDoc.data());
    if (ref == null) continue; // tombstone or unreadable — nothing to leave
    if (ref.hostUid === uid) continue; // own aircraft, handled by the recursive delete

    try {
      await adminDb.runTransaction(async (tx) => {
        const shareRef = adminDb.doc(thingShareDocPath(ref.hostUid, ref.thingId));
        const snap = await tx.get(shareRef);
        if (!snap.exists) return;
        tx.update(shareRef, { [`memberRoles.${uid}`]: FieldValue.delete() });
        tx.delete(adminDb.doc(shareMemberDocPath(ref.hostUid, ref.thingId, uid)));
      });
    } catch (e) {
      logger.error("Could not leave a share during account deletion", {
        uid,
        hostUid: ref.hostUid,
        thingId: ref.thingId,
        error: String(e),
      });
    }
  }
}

/**
 * Attachment bytes for the whole account. Blobs are aircraft-scoped under
 * `users/{uid}/{segment}/{acId}/blobs/`, so the single user prefix covers all of them
 * regardless of which entity segment the account is on during the migration window.
 *
 * Logged and swallowed on failure, like `onThingDeleted`'s sweep: orphaned bytes are picked up
 * by `scheduledStorageSweep`, whereas a throw here would abandon the deletion with the account
 * still live.
 */
async function deleteBlobs(uid: string): Promise<void> {
  const prefix = `users/${uid}/`;
  try {
    await adminStorage.bucket().deleteFiles({ prefix });
  } catch (e) {
    logger.error("Account blob sweep failed", { prefix, error: String(e) });
  }
}
