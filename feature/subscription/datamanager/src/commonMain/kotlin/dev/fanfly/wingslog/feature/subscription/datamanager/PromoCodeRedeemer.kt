package dev.fanfly.wingslog.feature.subscription.datamanager

/**
 * Spends a promo code for a term of SquawkIt Pro (#750).
 *
 * The client sends the code and nothing else. What it is worth, whether it has been spent, and
 * whether this account may have it are all decided server-side — the same rule the rest of the
 * billing pipeline follows, and the reason this returns a *result* rather than granting anything.
 * The entitlement arrives the long way round, through the synced `subscriptions/{uid}` doc, exactly
 * as a store purchase's does.
 */
interface PromoCodeRedeemer {

  /** Redeems [code] (in any typed form — case, spaces and dashes are normalized server-side). */
  suspend fun redeem(code: String): PromoRedemptionResult
}

/** What came back. Everything the page needs to decide what to say, and nothing else. */
sealed interface PromoRedemptionResult {

  /**
   * The server granted the term. Pro is **not** in force yet — the entitlement still has to sync
   * down, which is the same wait a completed purchase has.
   *
   * @param durationDays the term the code was worth, so the page can say "1 year" without doing
   *   date arithmetic on a value it would only get wrong across time zones.
   */
  data class Granted(val durationDays: Int) : PromoRedemptionResult

  /**
   * The code is not redeemable: unknown, already spent, or past its redemption window.
   *
   * Collapsed on purpose — the server refuses to distinguish them, because telling a guesser that a
   * code is real and merely spent confirms the shape of the live pool.
   */
  data object NotValid : PromoRedemptionResult

  /** Too many wrong codes from this account recently. The limiter is what keeps the pool safe. */
  data object TooManyAttempts : PromoRedemptionResult

  /**
   * The account is already on a paid store subscription, so the code was left unspent.
   *
   * Refused rather than applied: writing a comp over a paying subscriber would hand them a lapse
   * date while the store carried on billing.
   */
  data object AlreadySubscribed : PromoRedemptionResult

  /** Signed in as a guest. A guest account cannot outlive the device, so it holds no entitlement. */
  data object SignInRequired : PromoRedemptionResult

  /**
   * The server would not accept this copy of the app — App Check could not attest it.
   *
   * Kept apart from [SignInRequired] because the two look identical from the account's point of
   * view and have nothing in common as remedies: one is "sign in", the other is "this build cannot
   * prove it is SquawkIt". Telling a signed-in pilot to sign in sends them to re-check the one thing
   * that was never wrong.
   */
  data object AppUnverified : PromoRedemptionResult

  /** Offline, or the call failed. Nothing was spent; retrying is safe. */
  data object Unavailable : PromoRedemptionResult
}

/** Used where there is no backend to ask — tests, and any host without the callable wired. */
object NoOpPromoCodeRedeemer : PromoCodeRedeemer {
  override suspend fun redeem(code: String): PromoRedemptionResult =
    PromoRedemptionResult.Unavailable
}
