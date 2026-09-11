package dev.fanfly.wingslog.feature.subscription.model

/**
 * Promo-code format (#750). Must agree with `promoCodes.ts` on the server — the alphabet is what
 * makes a code survive being read off a badge or a postcard, and the length is what the entropy
 * argument rests on.
 */
private const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

/**
 * 12 symbols, four more than a share pairing code.
 *
 * An invite code can afford fewer because it dies in a day; a promo code sits redeemable in a pool
 * for as long as the campaign runs, and a guesser only has to hit any live member of that pool.
 */
const val PROMO_CODE_LENGTH: Int = 12

/**
 * Accepts what a human actually types — lowercase, spaces, and the `PRQK-8H3M-XTVB` grouping we
 * display — and returns the canonical code, or null if the input is not one.
 *
 * Separators are stripped; **anything else outside the alphabet is a rejection, not a deletion**,
 * for the same reason the share pairing codes made that rule: silently dropping unknown characters
 * turns junk into a well-formed code, so a pasted URL becomes a plausible-looking guess.
 */
fun normalizePromoCode(input: String): String? {
  val cleaned = input.uppercase()
    .filterNot { it == '-' || it.isWhitespace() }
  if (cleaned.length != PROMO_CODE_LENGTH) return null
  return if (cleaned.all { it in ALPHABET }) cleaned else null
}

/** `PRQK8H3MXTVB` → `PRQK-8H3M-XTVB`. Display only; the code itself is unformatted. */
fun formatPromoCode(code: String): String =
  if (code.length == PROMO_CODE_LENGTH) {
    "${code.take(4)}-${code.substring(4, 8)}-${code.drop(8)}"
  } else {
    code
  }
