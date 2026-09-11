package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.subscription.datamanager.PromoCodeRedeemer
import dev.fanfly.wingslog.feature.subscription.datamanager.PromoRedemptionResult
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * Calls the `redeemPromoCode` callable (#750).
 *
 * Sends only the typed code; the server normalizes it, decides what it is worth, and writes the
 * entitlement through the same single writer a purchase goes through. Nothing is granted here.
 *
 * ## Why the failure is classified by status code, not by message
 *
 * The callable's four refusals mean four different things to the pilot — retype it, sign in, wait,
 * or "you are already subscribed, and your code is unspent". Reading the server's English (as the
 * sharing callables do) would put untranslated backend copy on screen and break the moment a message
 * is reworded. The gRPC status code is the part of the contract that is stable and enumerable, so it
 * is what the mapping hangs on.
 */
class FirebasePromoCodeRedeemer(
  private val functions: FirebaseFunctions,
) : PromoCodeRedeemer {

  override suspend fun redeem(code: String): PromoRedemptionResult = try {
    val response = functions
      .httpsCallable("redeemPromoCode")
      .invoke(RedeemPromoRequest(code = code))
      .data<RedeemPromoResponse>()
    logger.i { "Promo code redeemed; days=${response.durationDays}" }
    PromoRedemptionResult.Granted(durationDays = response.durationDays)
  } catch (e: CancellationException) {
    throw e
  } catch (e: FirebaseFunctionsException) {
    // The code itself is never logged: it is a bearer secret, and a failed redeem is exactly the
    // case where it is most likely to be someone else's. The status code and message are ours.
    logger.w(e) { "Promo redeem refused: ${e.code}" }
    when (e.code) {
      // Unknown, spent, or expired — the server refuses to say which.
      FunctionsExceptionCode.NOT_FOUND -> PromoRedemptionResult.NotValid
      // Shape rejected before the pool was touched. Only reachable if the local and server
      // normalizers disagree, which is a bug rather than a user error — but it is still "that is
      // not a code", so it reads the same way.
      FunctionsExceptionCode.INVALID_ARGUMENT -> PromoRedemptionResult.NotValid
      FunctionsExceptionCode.RESOURCE_EXHAUSTED -> PromoRedemptionResult.TooManyAttempts
      FunctionsExceptionCode.FAILED_PRECONDITION -> PromoRedemptionResult.AlreadySubscribed
      FunctionsExceptionCode.PERMISSION_DENIED,
      FunctionsExceptionCode.UNAUTHENTICATED,
      -> PromoRedemptionResult.SignInRequired
      else -> PromoRedemptionResult.Unavailable
    }
  } catch (e: Exception) {
    // Everything that never became a callable error at all: no network, and — the case that cost a
    // debugging session — a request that failed to serialize, which never leaves the device and so
    // leaves no trace server-side. Both surface to the pilot as "couldn't reach SquawkIt", which is
    // only honest for the first, so the log has to name which it was.
    logger.w(e) { "Promo redeem failed before the server answered: ${e::class.simpleName}" }
    PromoRedemptionResult.Unavailable
  }

  private companion object {
    private val logger = Logger.withTag("PromoCodeRedeemer")
  }
}

/**
 * The request body. `internal` rather than `private` so `SubscriptionWireSerializationTest` can
 * prove a serializer actually exists for it — the failure this module shipped with was that none
 * did, and nothing about the code said so.
 */
@Serializable
internal data class RedeemPromoRequest(val code: String)

/**
 * Decodes the callable response. Defaulted so a missing key falls back rather than failing to
 * decode, matching the other callable clients in the repo.
 */
@Serializable
internal data class RedeemPromoResponse(
  val durationDays: Int = 0,
  val currentPeriodEndMillis: Long = 0L,
)
