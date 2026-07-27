package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.UnsupportedBillingManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
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
  ) = SubscriptionViewModel(
    subscriptionManager = FixedSubscriptionManager(status),
    billingManager = billingManager,
    entitlementReconciler = reconciler,
    activationGraceMillis = GRACE,
  )

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
