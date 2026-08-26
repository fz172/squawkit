package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
import dev.fanfly.wingslog.feature.subscription.model.UnsupportedBillingManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionUiStateTest {

  // `viewModelScope` dispatches on Main. Binding it to a TestDispatcher both makes the watchdog
  // runnable and hands `runTest` the same scheduler, so `advanceTimeBy` drives the delay.
  @Before
  fun setUpMainDispatcher() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDownMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun `pro status maps to isPro with lifecycle and storage carried through`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        status = Subscription.Status.STATUS_PRO,
        lifecycle = Subscription.Lifecycle.LIFECYCLE_ACTIVE,
        will_renew = true,
        storage_bytes_used = 240_000_000,
      ),
      TimeZone.UTC,
    )
    assertThat(state.isPro).isTrue()
    assertThat(state.lifecycle).isEqualTo(Subscription.Lifecycle.LIFECYCLE_ACTIVE)
    assertThat(state.willRenew).isTrue()
    assertThat(state.storageBytesUsed).isEqualTo(240_000_000)
  }

  @Test
  fun `free status is not pro`() {
    assertThat(
      toSubscriptionUiState(
        Subscription.Status.STATUS_FREE,
        Subscription(),
        TimeZone.UTC
      ).isPro
    )
      .isFalse()
  }

  @Test
  fun `the unmapped default state starts loading`() {
    // The only place a caller ever sees this default is the ViewModel's stateIn seed, before the
    // real combine has emitted — the page must not render Free/Pro from this value.
    assertThat(SubscriptionUiState().isLoading).isTrue()
  }

  @Test
  fun `a mapped state is never loading, free or pro`() {
    assertThat(
      toSubscriptionUiState(
        Subscription.Status.STATUS_FREE,
        Subscription(),
        TimeZone.UTC
      ).isLoading
    ).isFalse()
    assertThat(
      toSubscriptionUiState(
        Subscription.Status.STATUS_PRO,
        Subscription(),
        TimeZone.UTC
      ).isLoading
    ).isFalse()
  }

  @Test
  fun `member since is null when unset and formatted when present`() {
    val unset = toSubscriptionUiState(
      Subscription.Status.STATUS_FREE,
      Subscription(member_since_millis = 0L),
      TimeZone.UTC,
    )
    assertThat(unset.memberSince).isNull()

    val present = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(member_since_millis = 1_700_000_000_000L),
      TimeZone.UTC,
    )
    // 2023-11-14 UTC.
    assertThat(present.memberSince).isEqualTo("Nov 14, 2023")
  }

  @Test
  fun `purchase platform names the store that billed`() {
    assertThat(purchasePlatformOf("app_store")).isEqualTo(PurchasePlatform.APP_STORE)
    assertThat(purchasePlatformOf("mac_app_store")).isEqualTo(PurchasePlatform.MAC_APP_STORE)
    assertThat(purchasePlatformOf("play_store")).isEqualTo(PurchasePlatform.PLAY_STORE)
    assertThat(purchasePlatformOf("test_store")).isEqualTo(PurchasePlatform.TEST_STORE)
    // Play and Amazon cancel in completely different places, so they must not collapse together.
    assertThat(purchasePlatformOf("amazon")).isEqualTo(PurchasePlatform.AMAZON)
    assertThat(purchasePlatformOf("amazon")).isNotEqualTo(purchasePlatformOf("play_store"))
  }

  @Test
  fun `the web billers collapse into one entry`() {
    assertThat(purchasePlatformOf("stripe")).isEqualTo(PurchasePlatform.WEB)
    assertThat(purchasePlatformOf("rc_billing")).isEqualTo(PurchasePlatform.WEB)
    assertThat(purchasePlatformOf("paddle")).isEqualTo(PurchasePlatform.WEB)
  }

  @Test
  fun `grants and unrecognised platforms show no row rather than the word unknown`() {
    // A comp has no store to cancel at, so naming one would be worse than saying nothing.
    assertThat(purchasePlatformOf("server")).isNull()
    assertThat(purchasePlatformOf("promotional")).isNull()
    assertThat(purchasePlatformOf("unknown")).isNull()
    assertThat(purchasePlatformOf("")).isNull()
    // A value written by a newer server than this client falls into the same silent bucket.
    assertThat(purchasePlatformOf("some_future_store")).isNull()
  }

  @Test
  fun `activation watchdog asks the server to re-check when the entitlement never lands`() =
    runTest {
      // The charged-but-not-entitled case: the store took payment, no webhook arrived, and a daily
      // scan can never find this account because it holds no Pro entitlement to look stale.
      val reconciler = RecordingReconciler()
      val vm = viewModel(
        status = Subscription.Status.STATUS_FREE,
        reconciler = reconciler
      )

      vm.onPurchaseCompleted()
      advanceTimeBy((GRACE + 1).milliseconds)
      runCurrent()

      assertThat(reconciler.calls).isEqualTo(1)
    }

  @Test
  fun `activation watchdog stays quiet once the entitlement has landed`() =
    runTest {
      // Asking the server to re-check an account that is already Pro burns a provider lookup for
      // nothing, so the watchdog re-reads the tier rather than trusting the pending flag.
      val reconciler = RecordingReconciler()
      val vm = viewModel(
        status = Subscription.Status.STATUS_PRO,
        reconciler = reconciler
      )

      vm.onPurchaseCompleted()
      advanceTimeBy((GRACE + 1).milliseconds)
      runCurrent()

      assertThat(reconciler.calls).isEqualTo(0)
    }

  @Test
  fun `a second purchase restarts the watchdog rather than stacking another`() =
    runTest {
      val reconciler = RecordingReconciler()
      val vm = viewModel(
        status = Subscription.Status.STATUS_FREE,
        reconciler = reconciler
      )

      vm.onPurchaseCompleted()
      advanceTimeBy((GRACE / 2).milliseconds)
      vm.onPurchaseCompleted()
      advanceTimeBy((GRACE + 1).milliseconds)
      runCurrent()

      assertThat(reconciler.calls).isEqualTo(1)
    }

  @Test
  fun `purchase platform is read from the entitlement`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "play_store"),
      TimeZone.UTC,
    )
    assertThat(state.purchasePlatform).isEqualTo(PurchasePlatform.PLAY_STORE)
  }

  @Test
  fun `a subscription bought on this device's store can be managed here`() {
    assertThat(
      canManageHere(
        PurchasePlatform.PLAY_STORE,
        PurchasePlatform.PLAY_STORE
      )
    ).isTrue()
    assertThat(
      canManageHere(
        PurchasePlatform.APP_STORE,
        PurchasePlatform.APP_STORE
      )
    ).isTrue()
    // Both Apple storefronts are managed from the same place, so an iOS build handles either.
    assertThat(
      canManageHere(
        PurchasePlatform.MAC_APP_STORE,
        PurchasePlatform.APP_STORE
      )
    ).isTrue()
  }

  @Test
  fun `a subscription bought on another store cannot be managed here`() {
    // Pro is still unlocked on this device — entitlement is account-scoped — but Apple will not
    // cancel a Google Play plan, so the page must send the pilot back to the device that bought it.
    assertThat(
      canManageHere(
        PurchasePlatform.APP_STORE,
        PurchasePlatform.PLAY_STORE
      )
    ).isFalse()
    assertThat(
      canManageHere(
        PurchasePlatform.PLAY_STORE,
        PurchasePlatform.APP_STORE
      )
    ).isFalse()
    assertThat(
      canManageHere(
        PurchasePlatform.AMAZON,
        PurchasePlatform.PLAY_STORE
      )
    ).isFalse()
    // Web subscriptions are cancelled in the biller's own portal, never in the app.
    assertThat(
      canManageHere(
        PurchasePlatform.WEB,
        PurchasePlatform.PLAY_STORE
      )
    ).isFalse()
  }

  @Test
  fun `a build with no store manages nothing`() {
    // Web holds a real subscription and shows Pro, but has no Customer Center to open at all.
    assertThat(
      canManageHere(
        PurchasePlatform.PLAY_STORE,
        store = null
      )
    ).isFalse()
    assertThat(canManageHere(null, store = null)).isFalse()
    assertThat(
      canManageHere(
        PurchasePlatform.TEST_STORE,
        store = null
      )
    ).isFalse()
  }

  @Test
  fun `test store purchases stay manageable on the build that made them`() {
    // The simulated store's origin matches no real storefront. Treating that as a mismatch would
    // block managing every dogfood purchase, which is the only kind that exists before GA.
    assertThat(
      canManageHere(
        PurchasePlatform.TEST_STORE,
        PurchasePlatform.PLAY_STORE
      )
    ).isTrue()
    assertThat(
      canManageHere(
        PurchasePlatform.TEST_STORE,
        PurchasePlatform.APP_STORE
      )
    ).isTrue()
  }

  @Test
  fun `a grant with no store behind it is not treated as bought elsewhere`() {
    // A comp has nothing to cancel and no other device to be sent to, so blocking the button would
    // point the pilot at a platform that does not exist.
    assertThat(canManageHere(null, PurchasePlatform.PLAY_STORE)).isTrue()
  }

  @Test
  fun `canManage is resolved from the entitlement's origin store`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "app_store"),
      TimeZone.UTC,
      store = PurchasePlatform.PLAY_STORE,
    )
    assertThat(state.canManage).isFalse()
  }

  @Test
  fun `a comped account shows no manage affordance at all`() {
    // Both flavours of comp. Our admin RPC writes SERVER_GRANT + "server"; a promo granted from the
    // RevenueCat dashboard arrives as a webhook, which is why the server now labels a PROMOTIONAL
    // store as a grant too. Neither has anything to cancel.
    val adminGrant = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        source = Subscription.Source.SOURCE_SERVER_GRANT,
        origin_platform = "server",
      ),
      TimeZone.UTC,
      store = PurchasePlatform.PLAY_STORE,
    )
    val dashboardPromo = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "promotional"),
      TimeZone.UTC,
      store = PurchasePlatform.PLAY_STORE,
    )

    for (state in listOf(adminGrant, dashboardPromo)) {
      assertThat(state.isComped).isTrue()
      // Not the Customer Center, and not a link — the page renders nothing in that slot.
      assertThat(state.canManage).isFalse()
      assertThat(state.managementUrl).isNull()
      assertThat(state.purchasePlatform).isNull()
    }
  }

  @Test
  fun `a comp never offers a link even if one somehow got written`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        source = Subscription.Source.SOURCE_SERVER_GRANT,
        origin_platform = "server",
        management_url = "https://play.google.com/store/account/subscriptions",
      ),
      TimeZone.UTC,
      store = null,
    )

    assertThat(state.managementUrl).isNull()
  }

  @Test
  fun `an unrecognised store is not mistaken for a comp`() {
    // A purchase written by a newer server than this client still has a real store behind it and an
    // owner who may need to cancel — it must keep an affordance rather than falling silent.
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "some_future_store"),
      TimeZone.UTC,
      store = PurchasePlatform.PLAY_STORE,
    )

    assertThat(state.isComped).isFalse()
    assertThat(state.canManage).isTrue()
  }

  @Test
  fun `a comp is never asked to reconcile for a management url`() = runTest {
    val reconciler = RecordingReconciler()
    viewModel(
      status = Subscription.Status.STATUS_PRO,
      reconciler = reconciler,
      subscription = Subscription(
        source = Subscription.Source.SOURCE_SERVER_GRANT,
        origin_platform = "server",
      ),
    )
    runCurrent()

    assertThat(reconciler.calls).isEqualTo(0)
  }

  @Test
  fun `the synced management url is surfaced so a store-less build can still link out`() {
    // Web's only route to managing a subscription (#363): no billing SDK, so nothing local can
    // derive this — it has to arrive on the entitlement.
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        origin_platform = "play_store",
        management_url = "https://play.google.com/store/account/subscriptions",
      ),
      TimeZone.UTC,
      store = null,
    )
    assertThat(state.canManage).isFalse()
    assertThat(state.managementUrl).isEqualTo("https://play.google.com/store/account/subscriptions")
  }

  @Test
  fun `an unset management url falls back to the store's own subscriptions page`() {
    // Not the provider's deep link, but a real destination that works in a browser — strictly more
    // useful than telling the pilot to go find the device they bought it on (#361).
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "play_store"),
      TimeZone.UTC,
      store = null,
    )
    assertThat(state.managementUrl).isEqualTo("https://play.google.com/store/account/subscriptions")
    assertThat(state.isManagementUrlDerived).isTrue()
    assertThat(state.purchasePlatform).isEqualTo(PurchasePlatform.PLAY_STORE)
  }

  @Test
  fun `the provider's url always wins over the derived one`() {
    // The provider's deep-links to THIS subscription; ours only reaches the store's list.
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        origin_platform = "play_store",
        management_url = "https://play.google.com/store/account/subscriptions?sku=squawkit_pro_v1",
      ),
      TimeZone.UTC,
      store = null,
    )
    assertThat(state.managementUrl)
      .isEqualTo("https://play.google.com/store/account/subscriptions?sku=squawkit_pro_v1")
    assertThat(state.isManagementUrlDerived).isFalse()
  }

  @Test
  fun `the derived fallback covers every store that has a page`() {
    assertThat(derivedManagementUrlFor(PurchasePlatform.PLAY_STORE))
      .isEqualTo("https://play.google.com/store/account/subscriptions")
    // One Apple account page serves both storefronts, as canManageHere already assumes.
    assertThat(derivedManagementUrlFor(PurchasePlatform.APP_STORE))
      .isEqualTo("https://apps.apple.com/account/subscriptions")
    assertThat(derivedManagementUrlFor(PurchasePlatform.MAC_APP_STORE))
      .isEqualTo(derivedManagementUrlFor(PurchasePlatform.APP_STORE))
    assertThat(derivedManagementUrlFor(PurchasePlatform.AMAZON))
      .isEqualTo("https://www.amazon.com/gp/mas/your-account/myapps/yoursubscriptions")
  }

  @Test
  fun `no fallback is invented where none is correct`() {
    // WEB spans Stripe, RC Billing and Paddle, which share no portal — their management_url is the
    // only right answer. TEST_STORE is simulated and has no page. An unrecognised store is unknown
    // by definition. All three keep the honest "managed elsewhere" copy.
    assertThat(derivedManagementUrlFor(PurchasePlatform.WEB)).isNull()
    assertThat(derivedManagementUrlFor(PurchasePlatform.TEST_STORE)).isNull()
    assertThat(derivedManagementUrlFor(null)).isNull()
  }

  @Test
  fun `a test store purchase still shows the explanatory copy`() {
    // The dogfood case, and the reason this is a fallback rather than a replacement.
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "test_store"),
      TimeZone.UTC,
      store = null,
    )
    assertThat(state.managementUrl).isNull()
    assertThat(state.isManagementUrlDerived).isFalse()
  }


  @Test
  fun `a comp gets no derived fallback even when it names a store`() {
    // A grant should never carry a real storefront, but if one is ever written the comp rule has to
    // win — otherwise a comped pilot is sent to Play to cancel something Play never sold them.
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        source = Subscription.Source.SOURCE_SERVER_GRANT,
        origin_platform = "play_store",
      ),
      TimeZone.UTC,
      store = null,
    )
    assertThat(state.isComped).isTrue()
    assertThat(state.managementUrl).isNull()
    assertThat(state.isManagementUrlDerived).isFalse()
  }

  @Test
  fun `a management url that is not https is refused`() {
    // Second gate behind the server's own scheme check. This value came from a third party and is
    // handed to a URI handler — on web, straight to the browser — so `javascript:` must never reach
    // it, including from a doc written before the server validated.
    for (hostile in listOf(
      "javascript:alert(1)",
      "data:text/html,<script>alert(1)</script>",
      "http://play.google.com/store/account/subscriptions",
      "",
    )) {
      assertThat(manageableUrlOrNull(hostile)).isNull()
    }
  }

  @Test
  fun `a Pro account with no management link asks the server to reconcile`() =
    runTest {
      // The gap this closes: `management_url` is REST-only, so a purchase whose webhook worked is
      // never reconciled and never learns it — and the daily scan only looks at STALE entitlements, so
      // a healthy subscription would wait until it lapsed to get a link. Web has no billing SDK at
      // all, so without this it never gets one.
      val reconciler = RecordingReconciler()
      viewModel(
        status = Subscription.Status.STATUS_PRO,
        reconciler = reconciler,
        subscription = Subscription(origin_platform = "play_store"),
      )
      runCurrent()

      assertThat(reconciler.calls).isEqualTo(1)
    }

  @Test
  fun `a Pro account that already has a management link asks for nothing`() =
    runTest {
      val reconciler = RecordingReconciler()
      viewModel(
        status = Subscription.Status.STATUS_PRO,
        reconciler = reconciler
      )
      runCurrent()

      assertThat(reconciler.calls).isEqualTo(0)
    }

  @Test
  fun `a build whose own store sold the subscription does not ask for a link`() =
    runTest {
      // It opens the Customer Center instead, which is richer than any URL — asking would burn a
      // provider lookup for a link the page will never render.
      val reconciler = RecordingReconciler()
      viewModel(
        status = Subscription.Status.STATUS_PRO,
        reconciler = reconciler,
        billingManager = FakeBillingManager(PurchasePlatform.PLAY_STORE),
        subscription = Subscription(origin_platform = "play_store"),
      )
      runCurrent()

      assertThat(reconciler.calls).isEqualTo(0)
    }

  @Test
  fun `a free account is never asked about a management link`() = runTest {
    val reconciler = RecordingReconciler()
    viewModel(
      status = Subscription.Status.STATUS_FREE,
      reconciler = reconciler,
      subscription = Subscription(origin_platform = "play_store"),
    )
    runCurrent()

    assertThat(reconciler.calls).isEqualTo(0)
  }

  @Test
  fun `a guest cannot subscribe`() = runTest {
    // A guest account cannot be recovered on another device, so letting one buy a subscription
    // would take the pilot's money and tie it to an identity they can lose.
    val vm = viewModel(
      status = Subscription.Status.STATUS_FREE,
      reconciler = RecordingReconciler(),
      isGuest = true,
    )
    assertThat(stateOf(vm).isGuest).isTrue()
  }

  @Test
  fun `a signed-in account is not treated as a guest`() = runTest {
    val vm = viewModel(
      status = Subscription.Status.STATUS_FREE,
      reconciler = RecordingReconciler()
    )
    assertThat(stateOf(vm).isGuest).isFalse()
  }

  private companion object {
    private const val GRACE = 10_000L

    /**
     * The default fixture entitlement: Pro with a management link already known.
     *
     * Deliberately "already linked" so the watchdog tests below measure only the watchdog. The
     * ViewModel also asks for a reconcile when a Pro account has *no* management URL (#363), and a
     * default of `Subscription()` would fire that on construction and be counted as a watchdog call.
     */
    private val ALREADY_LINKED = Subscription(
      management_url = "https://play.google.com/store/account/subscriptions",
    )
  }

  /** A build that transacts with [store]; every other capability stays the unsupported no-op. */
  private class FakeBillingManager(
    override val store: PurchasePlatform?,
  ) : BillingManager by UnsupportedBillingManager

  /** Counts reconcile requests so a test can assert the watchdog fired exactly once. */
  private class RecordingReconciler : EntitlementReconciler {
    var calls = 0
      private set

    override suspend fun reconcileNow(): Boolean {
      calls++
      return false
    }
  }

  /** A ViewModel over a fixed tier, with the watchdog grace shortened to virtual time. */
  private fun viewModel(
    status: Subscription.Status,
    reconciler: EntitlementReconciler,
    billingManager: BillingManager = UnsupportedBillingManager,
    isGuest: Boolean = false,
    subscription: Subscription = ALREADY_LINKED,
    isAdsSupported: Boolean = false,
  ) = SubscriptionViewModel(
    subscriptionManager = FixedSubscriptionManager(status, subscription),
    billingManager = billingManager,
    entitlementReconciler = reconciler,
    authManager = authManager(isGuest),
    appCapability = AppCapability(
      isDeveloperOptionsSupported = false,
      isStressTestSupported = false,
      isNotificationsSupported = false,
      isCameraCaptureSupported = false,
      isAnonymousLoginSupported = false,
      isAdsSupported = isAdsSupported,
    ),
    activationGraceMillis = GRACE,
  )

  /**
   * The first state the ViewModel actually computes.
   *
   * `uiState` is a StateFlow seeded with a default and started `WhileSubscribed`, so reading it
   * without a collector just returns that placeholder — the upstream combine never runs.
   */
  private fun TestScope.stateOf(vm: SubscriptionViewModel): SubscriptionUiState {
    val job = launch { vm.uiState.collect { } }
    runCurrent()
    val state = vm.uiState.value
    job.cancel()
    return state
  }

  /** An AuthManager reporting a signed-in user who is, or is not, a guest. */
  private fun authManager(isGuest: Boolean): AuthManager =
    mockk(relaxed = true) {
      every { getCurrentUser() } returns mockk<FirebaseUser>(relaxed = true) {
        every { this@mockk.isAnonymous } returns isGuest
      }
    }

  private class FixedSubscriptionManager(
    private val status: Subscription.Status,
    private val subscription: Subscription = ALREADY_LINKED,
  ) : SubscriptionManager {
    override fun status(): Flow<Subscription.Status> = flowOf(status)
    override fun entitlement(): Flow<Subscription> = flowOf(subscription)
    override fun canUploadAttachments(): Flow<Boolean> = flowOf(false)
    override fun canEmailExports(): Flow<Boolean> = flowOf(false)
    override fun canHostShare(): Flow<Boolean> = flowOf(false)
    override fun aircraftLimit(): Flow<Int?> = flowOf(2)

    // These tests predate ads and assert nothing about them; false keeps the fake honest for a
    // subscriber, which is the tier they exercise.
    override fun shouldShowAds(): Flow<Boolean> = flowOf(false)
  }
}
