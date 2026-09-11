package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.firebase.functions.isCallableClientDefect
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * Calls the `reconcileMyEntitlement` callable.
 *
 * Sends no payload: the server derives the account from the caller's auth token, which is what makes
 * this safe to expose. There is nothing here a client could lie about.
 */
class FirebaseEntitlementReconciler(
  private val functions: FirebaseFunctions,
) : EntitlementReconciler {

  override suspend fun reconcileNow(): Boolean = try {
    val response = functions
      .httpsCallable("reconcileMyEntitlement")
      .invoke()
      .data<ReconcileResponseData>()
    logger.i { "Entitlement reconcile requested; corrected=${response.reconciled}" }
    response.reconciled
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    // Still swallowed, and still never shown to the pilot: this runs behind a purchase that already
    // succeeded, and the entitlement arrives on its own once the webhook lands or the daily
    // reconciler runs. What changed is that it is no longer swallowed *silently*.
    //
    // Severity is the routing decision — Error carrying a throwable becomes a Crashlytics non-fatal
    // (CrashBreadcrumbLogWriter) — so a defect is reported and weather is not. Before this, every
    // failure was one Warn line, which is how this call spent its whole life broken twice over
    // without anyone noticing (#951).
    if (e.isCallableClientDefect()) {
      logger.e(e) { "Entitlement reconcile is broken, not merely unavailable." }
    } else {
      logger.w(e) { "Entitlement reconcile request failed." }
    }
    false
  }

  private companion object {
    private val logger = Logger.withTag("EntitlementReconciler")
  }
}

/**
 * Decodes the callable response. Defaulted so a missing key falls back rather than failing to
 * decode, matching the other callable clients in the repo.
 *
 * `internal` and top-level so `SubscriptionWireSerializationTest` can reach it. It needs the cover
 * more than most: the module shipped without the serialization plugin, so no serializer was ever
 * generated for this type and every decode threw. The damage was bounded — the callable takes no
 * payload, so the request still went out and the server still reconciled and wrote the entitlement;
 * only the decoded `reconciled` flag was lost, and both call sites discard it. What it cost was the
 * evidence: the failure showed up as one swallowed warning per call, and nothing else.
 */
@Serializable
internal data class ReconcileResponseData(
  val reconciled: Boolean = false,
  val reason: String = "",
)
