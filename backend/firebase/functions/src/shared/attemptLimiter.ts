import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

import { adminDb } from "../config/firebaseAdmin.js";

/**
 * Per-account failed-attempt limiter for callables that dereference a short bearer code.
 *
 * Every such code in this codebase — a share pairing code, a promo code — trades entropy for being
 * typeable, and the trade is only safe because guessing is metered. This is that meter, factored out
 * so a second code type cannot ship with a second, subtly different copy of it.
 *
 * Each caller gets its **own counter collection**. Sharing one would let a burst of wrong invite
 * codes lock a pilot out of redeeming a promo code they hold legitimately, and the two budgets have
 * nothing to do with each other.
 *
 * Only FAILURES count. Succeeding, or dereferencing a code you legitimately hold, must never be
 * throttled — the limiter exists to stop guessing, not use.
 */
export type AttemptLimiter = {
  /**
   * Throws `resource-exhausted` if [uid] has burned through its failed attempts inside the window.
   * Call BEFORE dereferencing the code, so a locked-out caller learns nothing about it.
   */
  requireAttemptsRemaining(uid: string): Promise<void>;
  /**
   * Records one failed dereference. Starts a new window if the old one has aged out, so a slow
   * trickle of wrong guesses cannot accumulate into a permanent lockout for a legitimate user who
   * fat-fingered a code weeks apart.
   */
  recordFailedAttempt(uid: string): Promise<void>;
};

export type AttemptLimiterConfig = {
  /** Top-level collection holding one counter doc per uid. Functions-only in the rules. */
  collection: string;
  maxFailures: number;
  windowMs: number;
  /** What the caller is told when they are locked out. Must not reveal anything about the code. */
  message: string;
};

type AttemptsDoc = {
  failures: number;
  windowStartedAt: Timestamp;
};

export function createAttemptLimiter(config: AttemptLimiterConfig): AttemptLimiter {
  const docRef = (uid: string) => adminDb.doc(`${config.collection}/${uid}`);

  return {
    async requireAttemptsRemaining(uid: string): Promise<void> {
      const snap = await docRef(uid).get();
      if (!snap.exists) return;

      const attempts = snap.data() as AttemptsDoc;
      const windowAge = Date.now() - attempts.windowStartedAt.toMillis();
      if (windowAge >= config.windowMs) return; // stale window — the next failure starts a fresh one

      if (attempts.failures >= config.maxFailures) {
        throw new HttpsError("resource-exhausted", config.message);
      }
    },

    async recordFailedAttempt(uid: string): Promise<void> {
      const ref = docRef(uid);
      await adminDb.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const now = Timestamp.now();

        if (!snap.exists) {
          tx.set(ref, { failures: 1, windowStartedAt: now });
          return;
        }
        const attempts = snap.data() as AttemptsDoc;
        const windowAge = now.toMillis() - attempts.windowStartedAt.toMillis();
        if (windowAge >= config.windowMs) {
          tx.set(ref, { failures: 1, windowStartedAt: now });
          return;
        }
        tx.update(ref, { failures: FieldValue.increment(1) });
      });
    },
  };
}
