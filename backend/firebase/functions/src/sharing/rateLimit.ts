import { createAttemptLimiter } from "../shared/attemptLimiter.js";

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
 * The mechanism itself now lives in `shared/attemptLimiter.ts`, shared with promo-code redemption.
 * The budget stays this feature's own: a burst of wrong invite codes must not lock a pilot out of a
 * promo code they legitimately hold.
 */
const inviteAttemptLimiter = createAttemptLimiter({
  collection: "invite_attempts",
  maxFailures: 10,
  windowMs: 60 * 60 * 1000, // 1 hour
  message: "Too many invalid invite codes. Try again later.",
});

export const requireAttemptsRemaining = inviteAttemptLimiter.requireAttemptsRemaining;
export const recordFailedAttempt = inviteAttemptLimiter.recordFailedAttempt;
