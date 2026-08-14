package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import co.touchlab.kermit.Logger
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
    // Deliberately swallowed. This runs behind a purchase that already succeeded; the entitlement
    // still arrives on its own once the webhook lands or the daily reconciler runs, so a failure
    // here is a missed optimisation, not something to put in front of the pilot.
    logger.w(e) { "Entitlement reconcile request failed." }
    false
  }

  /**
   * Decodes the callable response. Defaulted so a missing key falls back rather than failing to
   * decode, matching the other callable clients in the repo.
   */
  @Serializable
  private data class ReconcileResponseData(
    val reconciled: Boolean = false,
    val reason: String = "",
  )

  private companion object {
    private val logger = Logger.withTag("EntitlementReconciler")
  }
}
