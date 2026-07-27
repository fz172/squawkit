package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the store's customer identity aliased to the signed-in SquawkIt account.
 *
 * This is the single most load-bearing line of the whole integration, and it is easy to miss: the
 * RevenueCat webhook identifies the purchaser only by its `app_user_id`. If that is RevenueCat's
 * generated anonymous id rather than the Firebase uid, the backend has no uid to write
 * `subscriptions/{uid}` for and the purchase silently never becomes an entitlement — the pilot is
 * charged and stays on Free. Calling `logIn(uid)` is what makes the webhook resolvable, and what
 * makes the same purchase visible on web (see `PlatformBillingModule`).
 *
 * On sign-out the store identity is reset to anonymous so the next account signing in on the same
 * device cannot inherit the previous one's receipts.
 *
 * A no-op on web, where [BillingManager] is the unsupported binding.
 */
class BillingIdentityCoordinator(
  private val firebaseAuth: FirebaseAuth,
  private val billingManager: BillingManager,
  private val scope: CoroutineScope,
) {

  private var job: Job? = null

  /** Idempotent: a second call while already running is ignored. */
  fun start() {
    if (!billingManager.isPurchaseSupported) {
      logger.d { "Purchasing unsupported on this build; not tracking store identity." }
      return
    }
    if (job?.isActive == true) return
    job = scope.launch {
      firebaseAuth.authStateChanged
        .map { it?.uid }
        // Auth re-emits on token refresh; only an actual account change needs a store login.
        .distinctUntilChanged()
        .collect { uid ->
          // Deliberately not logged with the uid at info level, per the repo's log-privacy rule.
          logger.d { "Binding store identity: ${if (uid == null) "signed out" else "signed in"}" }
          billingManager.setAppUserId(uid)
        }
    }
  }

  private companion object {
    private val logger = Logger.withTag("BillingIdentityCoordinator")
  }
}
