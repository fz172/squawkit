package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.PromoCodeRedeemer
import dev.fanfly.wingslog.feature.subscription.datamanager.PromoRedemptionResult
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.UnsupportedBillingManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Promo-code redemption on the subscription page (#750).
 *
 * The property under test throughout is that the client **grants nothing**: a successful redemption
 * only puts the page into the same "waiting for the entitlement to sync" state a purchase does, and
 * every refusal leaves the dialog open with the code intact so it can be corrected in place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromoCodeRedemptionTest {

  @Before
  fun setUpMainDispatcher() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDownMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun `typing crops to the code alphabet's length and uppercases`() {
    val vm = viewModel(PromoRedemptionResult.NotValid)

    vm.onPromoCodeChanged("prqk-8h3m-xtvb-extra")

    // Separators dropped, cropped at 12: the stored value is canonical, the dash is display-only.
    assertThat(vm.promoCodeState.value.code).isEqualTo("PRQK8H3MXTVB")
  }

  @Test
  fun `a code is only submittable once it is the right shape`() {
    val vm = viewModel(PromoRedemptionResult.NotValid)

    vm.onPromoCodeChanged("PRQK8H3M")
    assertThat(vm.promoCodeState.value.isComplete).isFalse()

    vm.onPromoCodeChanged("PRQK-8H3M-XTVB")
    assertThat(vm.promoCodeState.value.isComplete).isTrue()
  }

  @Test
  fun `a granted code closes the dialog and leaves the page waiting for the entitlement`() =
    runTest {
      val vm = viewModel(PromoRedemptionResult.Granted(durationDays = 365))
      vm.onPromoEntryOpened()
      vm.onPromoCodeChanged("PRQK-8H3M-XTVB")

      vm.onPromoCodeSubmitted()
      runCurrent()

      assertThat(vm.promoCodeState.value.isOpen).isFalse()
      val state = stateOf(vm)
      // Still Free: the tier changes only when the server-written entitlement syncs down.
      assertThat(state.isPro).isFalse()
      assertThat(state.isActivating).isTrue()
      assertThat(state.promoActivationTerm).isEqualTo(PromoTerm.ONE_YEAR)
    }

  @Test
  fun `a refusal keeps the dialog open with the code intact`() = runTest {
    val vm = viewModel(PromoRedemptionResult.NotValid)
    vm.onPromoEntryOpened()
    vm.onPromoCodeChanged("PRQK-8H3M-XTVB")

    vm.onPromoCodeSubmitted()
    runCurrent()

    val state = vm.promoCodeState.value
    assertThat(state.isOpen).isTrue()
    assertThat(state.isSubmitting).isFalse()
    assertThat(state.code).isEqualTo("PRQK8H3MXTVB")
    assertThat(state.error).isEqualTo(PromoCodeError.NOT_VALID)
  }

  @Test
  fun `each refusal maps to its own message`() = runTest {
    val cases = listOf(
      PromoRedemptionResult.TooManyAttempts to PromoCodeError.TOO_MANY_ATTEMPTS,
      PromoRedemptionResult.AlreadySubscribed to PromoCodeError.ALREADY_SUBSCRIBED,
      PromoRedemptionResult.SignInRequired to PromoCodeError.SIGN_IN_REQUIRED,
      // Distinct from SIGN_IN_REQUIRED on purpose: App Check rejecting the build and the pilot
      // being signed out look the same to the account and share no remedy.
      PromoRedemptionResult.AppUnverified to PromoCodeError.APP_UNVERIFIED,
      PromoRedemptionResult.Unavailable to PromoCodeError.UNAVAILABLE,
    )
    for ((result, expected) in cases) {
      val vm = viewModel(result)
      vm.onPromoCodeChanged("PRQK-8H3M-XTVB")
      vm.onPromoCodeSubmitted()
      runCurrent()
      assertThat(vm.promoCodeState.value.error).isEqualTo(expected)
    }
  }

  @Test
  fun `editing the code clears the previous refusal`() = runTest {
    val vm = viewModel(PromoRedemptionResult.NotValid)
    vm.onPromoCodeChanged("PRQK-8H3M-XTVB")
    vm.onPromoCodeSubmitted()
    runCurrent()

    vm.onPromoCodeChanged("PRQK-8H3M-XTVC")

    // Leaving the verdict under a code they are retyping reads as a verdict on the new one.
    assertThat(vm.promoCodeState.value.error).isNull()
  }

  @Test
  fun `a second submit is ignored while the first is in flight`() = runTest {
    val redeemer = BlockingRedeemer()
    val vm = viewModel(redeemer = redeemer)
    vm.onPromoCodeChanged("PRQK-8H3M-XTVB")

    vm.onPromoCodeSubmitted()
    runCurrent()
    vm.onPromoCodeSubmitted()
    runCurrent()

    // A second call would spend a second attempt against the guessing budget for nothing.
    assertThat(redeemer.calls).isEqualTo(1)
    assertThat(vm.promoCodeState.value.isSubmitting).isTrue()

    redeemer.complete(PromoRedemptionResult.NotValid)
    runCurrent()
    assertThat(vm.promoCodeState.value.isSubmitting).isFalse()
  }

  @Test
  fun `an incomplete code is never sent`() = runTest {
    val redeemer = RecordingRedeemer(PromoRedemptionResult.NotValid)
    val vm = viewModel(redeemer = redeemer)
    vm.onPromoCodeChanged("PRQK8H3M")

    vm.onPromoCodeSubmitted()
    runCurrent()

    assertThat(redeemer.calls).isEqualTo(0)
  }

  @Test
  fun `dismissing clears the entry so the next visit starts blank`() {
    val vm = viewModel(PromoRedemptionResult.NotValid)
    vm.onPromoEntryOpened()
    vm.onPromoCodeChanged("PRQK-8H3M-XTVB")

    vm.onPromoEntryDismissed()

    assertThat(vm.promoCodeState.value).isEqualTo(PromoCodeUiState())
  }

  @Test
  fun `the free tier is offered promo entry`() {
    val state = toSubscriptionUiState(
      status = Subscription.Status.STATUS_FREE,
      subscription = Subscription(),
    )
    assertThat(state.canRedeemPromo).isTrue()
  }

  @Test
  fun `a comped member may stack another code`() {
    val state = toSubscriptionUiState(
      status = Subscription.Status.STATUS_PRO,
      subscription = Subscription(
        source = Subscription.Source.SOURCE_SERVER_GRANT,
        origin_platform = "promotional",
      ),
    )
    assertThat(state.isComped).isTrue()
    assertThat(state.canRedeemPromo).isTrue()
  }

  @Test
  fun `a store subscriber is not offered a control the server would refuse`() {
    val state = toSubscriptionUiState(
      status = Subscription.Status.STATUS_PRO,
      subscription = Subscription(
        source = Subscription.Source.SOURCE_STORE_PURCHASE,
        origin_platform = "play_store",
      ),
    )
    assertThat(state.canRedeemPromo).isFalse()
  }

  @Test
  fun `a guest is not offered promo entry either`() {
    // A guest account cannot outlive the device, so a code spent by one is a code thrown away.
    val state = toSubscriptionUiState(
      status = Subscription.Status.STATUS_FREE,
      subscription = Subscription(),
      isGuest = true,
    )
    assertThat(state.canRedeemPromo).isFalse()
  }

  @Test
  fun `the promo activation line disappears once the entitlement lands`() {
    // Resolved from the tier rather than from the pending flag, so the page cannot stick on it.
    val state = toSubscriptionUiState(
      status = Subscription.Status.STATUS_PRO,
      subscription = Subscription(),
      promoActivationTerm = null,
    )
    assertThat(state.promoActivationTerm).isNull()
  }

  private fun viewModel(
    result: PromoRedemptionResult = PromoRedemptionResult.NotValid,
    redeemer: PromoCodeRedeemer = RecordingRedeemer(result),
    status: Subscription.Status = Subscription.Status.STATUS_FREE,
  ) = SubscriptionViewModel(
    subscriptionManager = FixedSubscriptionManager(status),
    billingManager = UnsupportedBillingManager,
    authManager = signedInAuthManager(),
    appCapability = AppCapability(
      isDeveloperOptionsSupported = false,
      isCameraCaptureSupported = false,
      isAnonymousLoginSupported = false,
      isAdsSupported = false,
    ),
    promoCodeRedeemer = redeemer,
  )

  /** See [SubscriptionUiStateTest]: `uiState` only computes once something collects it. */
  private fun TestScope.stateOf(vm: SubscriptionViewModel): SubscriptionUiState {
    val job = launch { vm.uiState.collect { } }
    runCurrent()
    val state = vm.uiState.value
    job.cancel()
    return state
  }

  private fun signedInAuthManager(): AuthManager =
    mockk(relaxed = true) {
      every { getCurrentUser() } returns mockk<FirebaseUser>(relaxed = true) {
        every { this@mockk.isAnonymous } returns false
      }
    }

  private class RecordingRedeemer(private val result: PromoRedemptionResult) : PromoCodeRedeemer {
    var calls = 0
      private set

    override suspend fun redeem(code: String): PromoRedemptionResult {
      calls++
      return result
    }
  }

  /** Holds the call open so the in-flight state can be observed and a double submit attempted. */
  private class BlockingRedeemer : PromoCodeRedeemer {
    private val gate = CompletableDeferred<PromoRedemptionResult>()
    var calls = 0
      private set

    override suspend fun redeem(code: String): PromoRedemptionResult {
      calls++
      return gate.await()
    }

    fun complete(result: PromoRedemptionResult) {
      gate.complete(result)
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
    override fun thingLimit(): Flow<Int?> = flowOf(2)
    override fun shouldShowAds(): Flow<Boolean> = flowOf(false)
  }
}
