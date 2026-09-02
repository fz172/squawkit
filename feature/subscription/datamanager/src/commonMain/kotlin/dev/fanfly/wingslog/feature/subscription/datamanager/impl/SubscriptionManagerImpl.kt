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
 * Reads the entitlement from the local [EntityStore] on an auth-scoped `flatMapLatest`, resolves the
 * effective tier, and applies the developer force-status override.
 *
 * @param forceStatus a developer override stream; emits a forced tier or `null` for "no override".
 *   Defaults to none; Developer Options wires the real source in P3. Honored only in developer
 *   builds ([AppCapability.isDeveloperOptionsSupported]), so a release build can never be forced premium.
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

  /**
   * Read once: [AppCapability] is fixed at build time, so there is no value change to observe and no
   * reason to re-evaluate it on every emission below.
   */
  private val devOverridesHonored = appCapability.isDeveloperOptionsSupported

  override fun status(): Flow<Subscription.Status> =
    combine(entitlement(), forceStatus) { subscription, forced ->
      // The forced tier wins, but only in a developer build — never in the shipping release.
      if (devOverridesHonored && forced != null) {
        forced
      } else {
        subscription.effectiveStatusAt(clock.now().toEpochMilliseconds())
      }
    }

  override fun canUploadAttachments(): Flow<Boolean> = gate(Subscription.Status.STATUS_PRO)

  override fun canEmailExports(): Flow<Boolean> = gate(Subscription.Status.STATUS_PRO)

  override fun canHostShare(): Flow<Boolean> = gate(Subscription.Status.STATUS_PRO)

  override fun thingLimit(): Flow<Int?> =
    status().map { if (it >= Subscription.Status.STATUS_PRO) null else FREE_THING_LIMIT }

  override fun shouldShowAds(): Flow<Boolean> =
    // No ads unless we can also sell their removal.
    if (!appCapability.isAdsSupported) {
      flowOf(false)
    } else {
      status().map { it < Subscription.Status.STATUS_PRO }
    }

  private fun gate(minimum: Subscription.Status): Flow<Boolean> =
    // Proto enums order by declaration (STATUS_FREE < STATUS_PRO), so this is a tier comparison.
    status().map { it >= minimum }

  companion object {
    private val logger = Logger.withTag("SubscriptionManagerImpl")
    private const val DOC_ID = "main"

    /**
     * Aircraft a free account may own; a Pro account is unlimited. Raised from 1 to 2 alongside
     * display ads (`docs/ads/display_ads_PRD.md` D9) — ad revenue on the free tier pays for the
     * second thing, and 2 covers the owner-plus-partnership case. Enforced client-side only;
     * no Cloud Function or Firestore rule checks thing count.
     */
    const val FREE_THING_LIMIT = 2
  }
}
