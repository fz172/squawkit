package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.BillingStore
import dev.fanfly.wingslog.feature.subscription.model.UnsupportedBillingManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import dev.fanfly.wingslog.core.model.settings.Subscription
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

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
    assertThat(toSubscriptionUiState(Subscription.Status.STATUS_FREE, Subscription(), TimeZone.UTC).isPro)
      .isFalse()
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
  fun `activation watchdog asks the server to re-check when the entitlement never lands`() = runTest {
    // The charged-but-not-entitled case: the store took payment, no webhook arrived, and a daily
    // scan can never find this account because it holds no Pro entitlement to look stale.
    val reconciler = RecordingReconciler()
    val vm = viewModel(status = Subscription.Status.STATUS_FREE, reconciler = reconciler)

    vm.onPurchaseCompleted()
    advanceTimeBy(GRACE + 1)
    runCurrent()

    assertThat(reconciler.calls).isEqualTo(1)
  }

  @Test
  fun `activation watchdog stays quiet once the entitlement has landed`() = runTest {
    // Asking the server to re-check an account that is already Pro burns a provider lookup for
    // nothing, so the watchdog re-reads the tier rather than trusting the pending flag.
    val reconciler = RecordingReconciler()
    val vm = viewModel(status = Subscription.Status.STATUS_PRO, reconciler = reconciler)

    vm.onPurchaseCompleted()
    advanceTimeBy(GRACE + 1)
    runCurrent()

    assertThat(reconciler.calls).isEqualTo(0)
  }

  @Test
  fun `a second purchase restarts the watchdog rather than stacking another`() = runTest {
    val reconciler = RecordingReconciler()
    val vm = viewModel(status = Subscription.Status.STATUS_FREE, reconciler = reconciler)

    vm.onPurchaseCompleted()
    advanceTimeBy(GRACE / 2)
    vm.onPurchaseCompleted()
    advanceTimeBy(GRACE + 1)
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
    assertThat(canManageHere(PurchasePlatform.PLAY_STORE, BillingStore.PLAY_STORE)).isTrue()
    assertThat(canManageHere(PurchasePlatform.APP_STORE, BillingStore.APP_STORE)).isTrue()
    // Both Apple storefronts are managed from the same place, so an iOS build handles either.
    assertThat(canManageHere(PurchasePlatform.MAC_APP_STORE, BillingStore.APP_STORE)).isTrue()
  }

  @Test
  fun `a subscription bought on another store cannot be managed here`() {
    // Pro is still unlocked on this device — entitlement is account-scoped — but Apple will not
    // cancel a Google Play plan, so the page must send the pilot back to the device that bought it.
    assertThat(canManageHere(PurchasePlatform.APP_STORE, BillingStore.PLAY_STORE)).isFalse()
    assertThat(canManageHere(PurchasePlatform.PLAY_STORE, BillingStore.APP_STORE)).isFalse()
    assertThat(canManageHere(PurchasePlatform.AMAZON, BillingStore.PLAY_STORE)).isFalse()
    // Web subscriptions are cancelled in the biller's own portal, never in the app.
    assertThat(canManageHere(PurchasePlatform.WEB, BillingStore.PLAY_STORE)).isFalse()
  }

  @Test
  fun `a build with no store manages nothing`() {
    // Web holds a real subscription and shows Pro, but has no Customer Center to open at all.
    assertThat(canManageHere(PurchasePlatform.PLAY_STORE, BillingStore.NONE)).isFalse()
    assertThat(canManageHere(null, BillingStore.NONE)).isFalse()
    assertThat(canManageHere(PurchasePlatform.TEST_STORE, BillingStore.NONE)).isFalse()
  }

  @Test
  fun `test store purchases stay manageable on the build that made them`() {
    // The simulated store's origin matches no real storefront. Treating that as a mismatch would
    // block managing every dogfood purchase, which is the only kind that exists before GA.
    assertThat(canManageHere(PurchasePlatform.TEST_STORE, BillingStore.PLAY_STORE)).isTrue()
    assertThat(canManageHere(PurchasePlatform.TEST_STORE, BillingStore.APP_STORE)).isTrue()
  }

  @Test
  fun `a grant with no store behind it is not treated as bought elsewhere`() {
    // A comp has nothing to cancel and no other device to be sent to, so blocking the button would
    // point the pilot at a platform that does not exist.
    assertThat(canManageHere(null, BillingStore.PLAY_STORE)).isTrue()
  }

  @Test
  fun `canManage is resolved from the entitlement's origin store`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "app_store"),
      TimeZone.UTC,
      store = BillingStore.PLAY_STORE,
    )
    assertThat(state.canManage).isFalse()
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
    val vm = viewModel(status = Subscription.Status.STATUS_FREE, reconciler = RecordingReconciler())
    assertThat(stateOf(vm).isGuest).isFalse()
  }

  private companion object {
    private const val GRACE = 10_000L
  }

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
  ) = SubscriptionViewModel(
    subscriptionManager = FixedSubscriptionManager(status),
    billingManager = billingManager,
    entitlementReconciler = reconciler,
    authManager = authManager(isGuest),
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
  private fun authManager(isGuest: Boolean): AuthManager = mockk(relaxed = true) {
    every { getCurrentUser() } returns mockk<FirebaseUser>(relaxed = true) {
      every { this@mockk.isAnonymous } returns isGuest
    }
  }

  private class FixedSubscriptionManager(
    private val status: Subscription.Status,
  ) : SubscriptionManager {
    override fun status(): Flow<Subscription.Status> = flowOf(status)
    override fun entitlement(): Flow<Subscription> = flowOf(Subscription())
    override fun canUploadAttachments(): Flow<Boolean> = flowOf(false)
    override fun canEmailExports(): Flow<Boolean> = flowOf(false)
    override fun canHostShare(): Flow<Boolean> = flowOf(false)
    override fun aircraftLimit(): Flow<Int?> = flowOf(1)
  }
}
