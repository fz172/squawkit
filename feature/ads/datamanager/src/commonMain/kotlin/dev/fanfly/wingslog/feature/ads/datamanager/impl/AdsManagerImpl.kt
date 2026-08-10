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

  // AppCapability is fixed at build time, so these are read once rather than re-evaluated on every
  // emission. They are plain booleans by nature — there is no value change to observe.
  private val adsPossible =
    appCapability.isAdsSupported && appCapability.isSubscriptionSupported
  private val devOverridesHonored = appCapability.isDeveloperOptionsSupported

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
    if (!adsPossible) return flowOf(false)
    if (!devOverridesHonored) return subscriptionManager.showsAds()
    return combine(
      subscriptionManager.showsAds(),
      forceAds
    ) { byTier, forced -> byTier || forced }
  }

  override fun headroom(): Int = counter.headroom

  override fun reserve(units: Int): Int = counter.reserve(units)

  override fun release(units: Int) = counter.release(units)

  override val capReached: Flow<Unit> = counter.capReached

}
