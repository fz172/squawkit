package dev.fanfly.wingslog.feature.ads.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.ads.model.AdSlotKey
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Only [showsAds] matters here — the tier resolution behind it is covered by
 * `SubscriptionManagerImplTest`, and duplicating it would just couple these tests to that logic.
 */
private class FakeSubscriptionGate(private val showsAds: Boolean) :
  SubscriptionManager {
  override fun status(): Flow<Subscription.Status> =
    flowOf(Subscription.Status.STATUS_FREE)

  override fun entitlement(): Flow<Subscription> = flowOf(Subscription())
  override fun canUploadAttachments(): Flow<Boolean> = flowOf(false)
  override fun canEmailExports(): Flow<Boolean> = flowOf(false)
  override fun canHostShare(): Flow<Boolean> = flowOf(false)
  override fun aircraftLimit(): Flow<Int?> = flowOf(2)
  override fun showsAds(): Flow<Boolean> = flowOf(showsAds)
}

class AdsManagerImplTest {

  private fun capability(
    ads: Boolean = true,
    subscription: Boolean = true,
    devOptions: Boolean = false,
  ) = AppCapability(
    isDeveloperOptionsSupported = devOptions,
    isAircraftSharingSupported = false,
    isStressTestSupported = false,
    isCameraCaptureSupported = false,
    isAnonymousLoginSupported = false,
    isAppleSignInSupported = false,
    isSubscriptionSupported = subscription,
    isAdsSupported = ads,
  )

  /** Stands in for SubscriptionManager.showsAds(), which is tested on its own in the subscription module. */
  private fun manager(
    capability: AppCapability,
    tierShowsAds: Boolean,
    forceAds: Flow<Boolean> = flowOf(false),
  ): AdsManagerImpl {
    val subscription = FakeSubscriptionGate(showsAds = tierShowsAds)
    return AdsManagerImpl(
      subscriptionManager = subscription,
      counter = AdSessionCounter(AppForegroundObserver()),
      appCapability = capability,
      forceAds = forceAds,
    )
  }

  @Test
  fun `free tier on a supported build shows ads`() = runTest {
    val m = manager(capability(), tierShowsAds = true)
    assertThat(
      m.showsAds()
        .first()
    ).isTrue()
  }

  @Test
  fun `a paid tier is ad-free`() = runTest {
    val m = manager(capability(), tierShowsAds = false)
    assertThat(
      m.showsAds()
        .first()
    ).isFalse()
  }

  @Test
  fun `ads unsupported means no ads, even on the free tier`() = runTest {
    val m = manager(capability(ads = false), tierShowsAds = true)
    assertThat(
      m.showsAds()
        .first()
    ).isFalse()
  }

  @Test
  fun `no subscription support means no ads - the inversion`() = runTest {
    // This is the opposite of every sibling gate, and the one most likely to be "fixed" into a bug.
    // Every other capability is default-OPEN: with subscriptions off there is no paywall, so they
    // all read available. Ads are default-CLOSED, because a build that cannot sell Heavy gives a
    // pilot no way to remove ads.
    val m = manager(capability(subscription = false), tierShowsAds = true)
    assertThat(
      m.showsAds()
        .first()
    ).isFalse()
  }

  // -------------------------------------------------------------- developer force

  @Test
  fun `force ads overrides the tier in a developer build`() = runTest {
    val m = manager(
      capability(devOptions = true),
      tierShowsAds = false, // a paid account
      forceAds = flowOf(true),
    )
    assertThat(
      m.showsAds()
        .first()
    ).isTrue()
  }

  @Test
  fun `force ads is ignored in a release build`() = runTest {
    val m = manager(
      capability(devOptions = false),
      tierShowsAds = false,
      forceAds = flowOf(true),
    )
    assertThat(
      m.showsAds()
        .first()
    ).isFalse()
  }

  @Test
  fun `force ads never overrides the capability gates`() = runTest {
    // Forcing ads onto a Heavy account exercises placement, which is the point. Forcing them into a
    // build with no way to buy removal would be testing a state that must never exist.
    val noAds =
      manager(capability(ads = false, devOptions = true), true, flowOf(true))
    val noSubs = manager(
      capability(subscription = false, devOptions = true),
      true,
      flowOf(true)
    )
    assertThat(
      noAds.showsAds()
        .first()
    ).isFalse()
    assertThat(
      noSubs.showsAds()
        .first()
    ).isFalse()
  }

  @Test
  fun `showsAds reacts to the force toggle without re-subscription`() =
    runTest {
      val force = MutableStateFlow(false)
      val m = manager(
        capability(devOptions = true),
        tierShowsAds = false,
        forceAds = force
      )

      assertThat(
        m.showsAds()
          .first()
      ).isFalse()
      force.value = true
      assertThat(
        m.showsAds()
          .first()
      ).isTrue()
    }

  // ------------------------------------------------------------------------- budget

  @Test
  fun `budget calls delegate to the counter`() {
    val m = manager(capability(), tierShowsAds = true)
    assertThat(m.reserve(AdSlotKey(AdSurface.SQUAWKS, 0), 2)).isEqualTo(2)
    // Distinct slots keep spending until the cap binds.
    val rest = (1..AdSessionCounter.CAP).sumOf {
      m.reserve(
        AdSlotKey(
          AdSurface.SQUAWKS,
          it
        ), 1
      )
    }
    assertThat(rest).isEqualTo(AdSessionCounter.CAP - 2)
  }

  @Test
  fun `the same slot re-reserved keeps its grant without spending more`() {
    val m = manager(capability(), tierShowsAds = true)
    val key = AdSlotKey(AdSurface.LOGS, 0)

    assertThat(m.reserve(key, 1)).isEqualTo(1)
    assertThat(m.reserve(key, 1)).isEqualTo(1)
    // The rest of the budget is still available to other slots, so only one unit was spent.
    val others = (1..AdSessionCounter.CAP).sumOf {
      m.reserve(
        AdSlotKey(AdSurface.LOGS, it),
        1
      )
    }
    assertThat(others).isEqualTo(AdSessionCounter.CAP - 1)
  }

  @Test
  fun `impressions are marked once per slot`() {
    val m = manager(capability(), tierShowsAds = true)
    val key = AdSlotKey(AdSurface.TASKS, 2)

    assertThat(m.markImpressionLogged(key)).isTrue()
    assertThat(m.markImpressionLogged(key)).isFalse()
  }
}
