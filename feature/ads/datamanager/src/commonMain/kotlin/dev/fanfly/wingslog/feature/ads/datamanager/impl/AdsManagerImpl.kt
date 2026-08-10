package dev.fanfly.wingslog.feature.ads.datamanager.impl

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Combines the entitlement gate with the session budget, and applies the developer force-override.
 *
 * See [SubscriptionManager.showsAds] for why the tier gate is default-CLOSED.
 */
internal class AdsManagerImpl(
  private val subscriptionManager: SubscriptionManager,
  private val counter: AdSessionCounter,
  private val appCapability: AppCapability,
  private val forceAds: Flow<Boolean>,
) : AdsManager {

  /**
   * **Force overrides the tier check, never the capability checks.**
   *
   * A developer forcing ads on a Heavy account is exercising placement, which is the point of the
   * toggle. A developer forcing ads into a build with no `isAdsSupported` — or worse, no
   * `isSubscriptionSupported`, where a pilot has no way to buy their way out — would be testing a
   * state that must never exist. So the capability gate short-circuits before the override is even
   * consulted.
   */
  override fun showsAds(): Flow<Boolean> {
    if (!appCapability.isAdsSupported || !appCapability.isSubscriptionSupported) return flowOf(
      false
    )
    return combine(subscriptionManager.showsAds(), forceAds) { byTier, forced ->
      byTier || (forced && appCapability.isDeveloperOptionsSupported)
    }
  }

  override fun headroom(): Int = counter.headroom

  override fun reserve(units: Int): Int = counter.reserve(units)

  override val capReached: Flow<Unit> = counter.capReached

  override fun resetSessionForDeveloper() {
    // Ignored outside developer builds, so a release build cannot be talked into a fresh budget.
    if (!appCapability.isDeveloperOptionsSupported) return
    counter.resetSession()
  }
}
