import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

import { adminDb } from "../config/firebaseAdmin.js";

/**
 * Failed-attempt limiter for the two callables that dereference an invite code (#164).
 *
 * A pairing code carries ~39 bits, not the 128 the old link secret did. That is acceptable ONLY
 * because guessing is metered: a code dies after one day and after one use, and an attacker gets a
 * small number of tries. Remove this and 30^8 is walkable with a script.
 *
 * **Both** code-dereferencing callables must go through it — redeem *and* preview. Rate-limiting
 * redeem alone would leave preview as a free oracle: it answers "is this code real?" just as well,
 * and it is the one you forget, because it feels read-only.
 *
 * Only FAILURES count. A member redeeming successfully, or previewing a code they legitimately hold,
 * should never be throttled — the limiter exists to stop guessing, not use.
 */
const MAX_FAILURES = 10;
const WINDOW_MS = 60 * 60 * 1000; // 1 hour

const ATTEMPTS_COLLECTION = "invite_attempts";

type AttemptsDoc = {
  failures: number;
  windowStartedAt: Timestamp;
};

/**
 * Throws `resource-exhausted` if [uid] has burned through its failed attempts inside the window.
 * Call BEFORE dereferencing the code, so a locked-out caller learns nothing about it.
 */
export async function requireAttemptsRemaining(uid: string): Promise<void> {
  const snap = await adminDb.doc(`${ATTEMPTS_COLLECTION}/${uid}`).get();
  if (!snap.exists) return;

  const attempts = snap.data() as AttemptsDoc;
  const windowAge = Date.now() - attempts.windowStartedAt.toMillis();
  if (windowAge >= WINDOW_MS) return; // stale window — the next failure starts a fresh one

  if (attempts.failures >= MAX_FAILURES) {
    throw new HttpsError(
      "resource-exhausted",
      "Too many invalid invite codes. Try again later.",
    );
  }
}

/**
 * Records one failed dereference. Starts a new window if the old one has aged out, so a slow trickle
 * of wrong guesses cannot accumulate into a permanent lockout for a legitimate user who fat-fingered
 * a code weeks apart.
 */
export async function recordFailedAttempt(uid: string): Promise<void> {
  const ref = adminDb.doc(`${ATTEMPTS_COLLECTION}/${uid}`);
  await adminDb.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const now = Timestamp.now();

    if (!snap.exists) {
      tx.set(ref, { failures: 1, windowStartedAt: now });
      return;
    }
    const attempts = snap.data() as AttemptsDoc;
    const windowAge = now.toMillis() - attempts.windowStartedAt.toMillis();
    if (windowAge >= WINDOW_MS) {
      tx.set(ref, { failures: 1, windowStartedAt: now });
      return;
    }
    tx.update(ref, { failures: FieldValue.increment(1) });
  });
}
