import { randomInt } from "node:crypto";

/**
 * Promo codes — a pre-minted pool of single-use handles that grant SquawkIt Pro for a fixed term
 * (#750).
 *
 * The code IS the secret, and it lives in its own top-level collection keyed by the code itself.
 * **No client may read or write it** (see `firestore.rules`); only `redeemPromoCode` dereferences
 * one, as admin. A client that could read this collection could read every unredeemed code in it,
 * which is the whole pool — so the collection is denied outright rather than exposed behind a filter.
 *
 * ## Why the pool is pre-minted rather than derived
 *
 * A derived code (an HMAC of a duration, say) needs no storage but cannot be *spent*: the server has
 * to remember which ones were used anyway, and then it has a pool with extra steps and no way to
 * withdraw a batch that leaked. A row per code is the simpler thing, and it is what makes
 * requirement 3 — "marked as used in the pool, and by whom" — a single field rather than a ledger.
 *
 * ## Single-use is a claim, not a deletion
 *
 * Unlike an invite code, a redeemed promo code is **kept** with `redeemedByUid` set. An invite is
 * private correspondence between two people and deleting it is the cleanest possible "already used";
 * a promo code is a marketing artifact whose whole point is being counted afterwards — which batch
 * converted, how many of a run were spent, who redeemed which. Deleting the row throws that away.
 *
 * The redemption error deliberately does not distinguish "already used" from "never existed", for
 * the same reason invites do not: telling a guesser that a code is real, merely spent, confirms the
 * shape of the live pool for them. The one exception is the redeemer themselves — a code already
 * redeemed by *this* uid replays as a success, so a dropped response or a double tap is not a
 * mysterious failure over an entitlement they already hold.
 */

/** Top-level, functions-only. Keyed by the normalized code. */
export const PROMO_CODES_COLLECTION = "promo_codes";

export function promoCodeDocPath(code: string): string {
  return `${PROMO_CODES_COLLECTION}/${code}`;
}

/** Failed-redemption counters behind the limiter, one doc per uid. Functions-only. */
export const PROMO_ATTEMPTS_COLLECTION = "promo_attempts";

/**
 * One minted code. Plain fields, written by the generator script and claimed by the callable.
 *
 * Millis rather than `Timestamp` throughout, matching `subscriptions/{uid}` — these values end up
 * compared against `Date.now()` and against `currentPeriodEndMillis`, and one representation across
 * the entitlement pipeline beats two.
 */
export type PromoCodeDoc = {
  /** How much Pro the code is worth, in days. Written at mint time; redemption never chooses it. */
  durationDays: number;
  /**
   * Free-text label for the run this code was minted in ("launch-2026", "oshkosh"), so a batch can
   * be counted, or withdrawn, as a unit. Not a secret and not load-bearing — the code is.
   */
  batch: string;
  createdAtMillis: number;
  /**
   * When the code stops being redeemable, or `0` for never.
   *
   * Distinct from [durationDays]: this bounds how long the code may be *spent*, the other how long
   * the Pro it buys lasts. A conference giveaway wants a short redemption window and a full year of
   * Pro, and collapsing the two would force a choice between them.
   */
  expiresAtMillis: number;
  /** The account that spent it, or `null` while unspent. Requirement 3's "by whom". */
  redeemedByUid: string | null;
  redeemedAtMillis: number | null;
  /**
   * The entitlement end date this redemption produced, or `null` while unspent.
   *
   * Recorded rather than recomputed: it is what makes a retry return the same date as the first
   * attempt instead of sliding forward, and it is the only place the spent pool says what was
   * actually given away — [durationDays] alone does not, since a code stacked onto existing comp
   * time grants past it.
   */
  grantedUntilMillis: number | null;
};

/**
 * Unambiguous read aloud and typed on a phone: no `0/O`, `1/I/L`, or `U` (heard as "you"). The same
 * alphabet the share pairing codes use, for the same reason — a code gets read off a conference
 * badge or a postcard at least as often as it gets pasted.
 */
const ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

/**
 * 12 symbols, not the invite code's 8: 30^12 ≈ 5.3e17, about 59 bits.
 *
 * An invite code can afford 39 bits because it dies in a day. A promo code cannot — it sits
 * redeemable in a pool for as long as the campaign runs, and a pool is a *larger* target than one
 * invite, since a guesser only has to hit any live member of it. Four more symbols is the cheapest
 * possible answer to that and still fits in three spoken groups.
 */
const CODE_LENGTH = 12;

/** CSPRNG. `randomInt` is rejection-sampled, so no modulo bias across the 30-symbol alphabet. */
export function generatePromoCode(): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += ALPHABET[randomInt(ALPHABET.length)];
  }
  return code;
}

/**
 * Accepts what a human actually types — lowercase, spaces, and the `PRQK-8H3M-XTVB` grouping we
 * display in — and returns the canonical code, or "" if the input is not one.
 *
 * Separators are stripped; anything else outside the alphabet is a **rejection, not a deletion**.
 * Silently dropping unknown characters turns junk into a well-formed code, which is how a pasted URL
 * becomes a plausible-looking guess. Must match `PromoCode.kt` on the client.
 */
export function normalizePromoCode(input: string): string {
  const cleaned = input
    .toUpperCase()
    .split("")
    .filter((ch) => ch !== "-" && !/\s/.test(ch))
    .join("");
  if (cleaned.length !== CODE_LENGTH) return "";
  return cleaned.split("").every((ch) => ALPHABET.includes(ch)) ? cleaned : "";
}

/** `PRQK8H3MXTVB` → `PRQK-8H3M-XTVB`. Display only; the stored id is unformatted. */
export function formatPromoCode(code: string): string {
  if (code.length !== CODE_LENGTH) return code;
  return `${code.slice(0, 4)}-${code.slice(4, 8)}-${code.slice(8)}`;
}

/**
 * The named terms a code can be minted for, mapping to [PromoCodeDoc.durationDays].
 *
 * Calendar-agnostic day counts on purpose. A promo is a fixed amount of Pro, not a subscription
 * aligned to a billing anniversary, so "3 months" meaning 90 days is both simpler and never off by
 * a day depending on which month it was redeemed in.
 */
export const PROMO_DURATIONS: Record<string, number> = {
  "1m": 30,
  "3m": 90,
  "1y": 365,
};
