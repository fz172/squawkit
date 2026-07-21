package dev.fanfly.wingslog.feature.subscription.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.effectiveStatusAt
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Mirrors [dev.fanfly.wingslog.feature.featurelab.datamanager.impl.FeatureLabManagerImpl]'s shape:
 * an auth-scoped `flatMapLatest` over the local [EntityStore], plus lifecycle resolution, the
 * default-open rollout gate, and the developer force-status override.
 *
 * @param forceStatus a developer override stream; emits a forced tier or `null` for "no override".
 *   Defaults to none; Developer Options wires the real source in P3. Honored only in developer
 *   builds ([AppCapability.isFeatureLabSupported]), so a release build can never be forced premium.
 */
class SubscriptionManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
  private val appCapability: AppCapability,
  private val forceStatus: Flow<Subscription.Status?> = flowOf(null),
  private val clock: Clock = Clock.System,
) : SubscriptionManager {

  private val store: EntityStore<Subscription> =
    storeFactory.create(CollectionKind.Subscription)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun entitlement(): Flow<Subscription> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        flowOf(Subscription())
      } else {
        store.observe(DOC_ID, EntityScope.userRoot(user.uid))
          .map { it?.value ?: Subscription() }
          .catch { e ->
            logger.w(e) { "Error observing subscription entitlement" }
            emit(Subscription())
          }
      }
    }

  override fun status(): Flow<Subscription.Status> =
    combine(entitlement(), forceStatus) { subscription, override ->
      // The override wins, but only in a developer build — never in the shipping release.
      if (appCapability.isFeatureLabSupported && override != null) {
        override
      } else {
        subscription.effectiveStatusAt(clock.now().toEpochMilliseconds())
      }
    }

  override fun canUploadAttachments(): Flow<Boolean> = gate(Subscription.Status.STATUS_PRO)

  override fun canEmailExports(): Flow<Boolean> = gate(Subscription.Status.STATUS_PRO)

  override fun canHostShare(): Flow<Boolean> = gate(Subscription.Status.STATUS_PRO)

  override fun aircraftLimit(): Flow<Int?> =
    // Default-open: no paywall while the capability is off → unlimited.
    if (!appCapability.isSubscriptionSupported) {
      flowOf(null)
    } else {
      status().map { if (it >= Subscription.Status.STATUS_PRO) null else FREE_AIRCRAFT_LIMIT }
    }

  private fun gate(minimum: Subscription.Status): Flow<Boolean> =
    // Default-open: while the subscription capability is off, every gate reads available.
    if (!appCapability.isSubscriptionSupported) {
      flowOf(true)
    } else {
      // Proto enums order by declaration (STATUS_FREE < STATUS_PRO), so this is a tier comparison.
      status().map { it >= minimum }
    }

  companion object {
    private val logger = Logger.withTag("SubscriptionManagerImpl")
    private const val DOC_ID = "main"

    /** Aircraft a free account may own; a Pro account is unlimited. */
    const val FREE_AIRCRAFT_LIMIT = 1
  }
}
