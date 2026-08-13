package dev.fanfly.wingslog.feature.login.upgrade

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.storage.LocalAccountMigrator
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

internal fun testAppCapability() = AppCapability(
  isDeveloperOptionsSupported = false,
  isAircraftSharingSupported = true,
  isStressTestSupported = false,
  isCameraCaptureSupported = false,
  isAnonymousLoginSupported = true,
  isSubscriptionSupported = false,
  isAdsSupported = false,
)

private const val GUEST_UID = "guest-1"
private const val EMAIL = "pilot@example.com"
private const val LINK = "https://squawkit.fanfly.dev/finishSignIn?oobCode=abc"

/**
 * The picker, and the rule that an inbound email link is never acted on by itself.
 *
 * Linking binds the address to *this* device's guest data. A link opened against a different guest
 * session than the one that asked for it would silently attach someone's records to the wrong
 * account, so the flow always stops at a confirmation the user has to accept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeProviderPickerTest {

  private val dispatcher = UnconfinedTestDispatcher()

  private val authManager: AuthManager = mockk(relaxed = true)
  private val migrator: LocalAccountMigrator = mockk(relaxed = true)
  private val technicianManager: TechnicianManager = mockk(relaxed = true)
  private val syncEngine: SyncEngine = mockk(relaxed = true)
  private val emailStore: UpgradeEmailStore = mockk(relaxed = true)

  private val guest: FirebaseUser = mockk {
    every { uid } returns GUEST_UID
    every { isAnonymous } returns true
  }

  private val permanent: FirebaseUser = mockk {
    every { uid } returns GUEST_UID
    every { isAnonymous } returns false
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    EmailLinkDeepLinks.consume()
    every { authManager.getCurrentUser() } returns guest
    every { authManager.isSignInWithEmailLink(any()) } returns true
    coEvery { emailStore.pendingEmail(any()) } returns null
  }

  @After
  fun tearDown() {
    EmailLinkDeepLinks.consume()
    Dispatchers.resetMain()
  }

  private fun viewModel() = AccountUpgradeViewModel(
    authManager = authManager,
    migrator = migrator,
    technicianManager = technicianManager,
    syncEngine = syncEngine,
    emailStore = emailStore,
  )

  /**
   * All three providers, on every platform. Both capability gates that used to prune this list are
   * gone — Google's with #415, Apple's with #408 — each once the limitation it stood for was fixed.
   *
   * The **order** is asserted, not just the membership: the picker offers the same choice as the
   * full-screen login page, which lists Google, then Apple, then email. Having the two disagree is
   * what made the sheet feel like a different screen rather than the same one in a different place.
   */
  @Test
  fun choose_offersEveryProviderInTheLoginPagesOrder() = runTest(dispatcher) {
    val vm = viewModel().also { it.choose() }

    assertThat((vm.state.value as UpgradeUiState.ChoosingProvider).providers)
      .containsExactly(AuthProvider.Google, AuthProvider.Apple, AuthProvider.Email)
      .inOrder()
  }

  /**
   * Linking never fires authStateChanged, and the flow drops straight from Success back to Idle, so
   * a host watching only [UpgradeUiState] can miss the transition and keep showing "Log in".
   */
  @Test
  fun completions_emitOnceTheUpgradeSucceeds() = runTest(dispatcher) {
    coEvery { authManager.upgradeAnonymousAccount(AuthProvider.Google) } returns
      AccountUpgradeResult.Linked(permanent)
    every { authManager.getCurrentUser() } returnsMany listOf(guest, permanent, permanent, permanent)
    val vm = viewModel()
    val seen = mutableListOf<Unit>()
    val job = launch { vm.completions.toList(seen) }

    vm.startUpgrade(AuthProvider.Google)
    advanceUntilIdle()

    assertThat(seen).hasSize(1)
    job.cancel()
  }

  @Test
  fun selectingEmail_asksForAnAddressInsteadOfStartingAProviderFlow() = runTest(dispatcher) {
    val vm = viewModel().also { it.select(AuthProvider.Email) }

    assertThat(vm.state.value).isInstanceOf(UpgradeUiState.EnteringEmail::class.java)
    coVerify(exactly = 0) { authManager.upgradeAnonymousAccount(any()) }
  }

  @Test
  fun sendEmailLink_stashesTheAddressAgainstTheGuestUid() = runTest(dispatcher) {
    coEvery { authManager.sendSignInLink(EMAIL) } returns SendLinkResult.Sent(EMAIL)
    val vm = viewModel().also { it.select(AuthProvider.Email) }

    vm.setEmail(EMAIL)
    vm.sendEmailLink()
    advanceUntilIdle()

    coVerify { emailStore.savePendingEmail(GUEST_UID, EMAIL) }
    assertThat(vm.state.value).isEqualTo(UpgradeUiState.LinkSent(EMAIL))
  }

  @Test
  fun sendEmailLink_keepsTheTypedAddressWhenItIsRejected() = runTest(dispatcher) {
    coEvery { authManager.sendSignInLink(any()) } returns SendLinkResult.InvalidEmail
    val vm = viewModel().also { it.select(AuthProvider.Email) }

    vm.setEmail("nope")
    vm.sendEmailLink()
    advanceUntilIdle()

    val state = vm.state.value as UpgradeUiState.EnteringEmail
    assertThat(state.email).isEqualTo("nope")
    assertThat(state.error).isNotNull()
    assertThat(state.sending).isFalse()
  }

  /**
   * Closing "check your email" is the user doing what it asked — leaving to open the link. Clearing
   * the stash there deleted the pending upgrade before the link could be used, so the returning
   * link was ignored for having nothing pending, and email upgrade never worked at all.
   */
  @Test
  fun dismissingLinkSent_keepsThePendingUpgrade() = runTest(dispatcher) {
    coEvery { authManager.sendSignInLink(EMAIL) } returns SendLinkResult.Sent(EMAIL)
    val vm = viewModel().also { it.select(AuthProvider.Email) }
    vm.setEmail(EMAIL)
    vm.sendEmailLink()
    advanceUntilIdle()

    vm.cancel()
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(UpgradeUiState.Idle)
    coVerify(exactly = 0) { emailStore.clear(any()) }
  }

  @Test
  fun cancellingTheConfirmation_discardsTheStash() = runTest(dispatcher) {
    coEvery { emailStore.pendingEmail(GUEST_UID) } returns EMAIL
    val vm = viewModel()
    vm.onIncomingLink(LINK)
    advanceUntilIdle()

    // "If that isn't your address, cancel and start again" — so the stash must not survive.
    vm.cancel()
    advanceUntilIdle()

    coVerify { emailStore.clear(GUEST_UID) }
  }

  /**
   * Apple's identity token is single-use, so merging needs a second sheet. That prompt must not
   * appear until the user has been told why — otherwise it reads as the first attempt failing,
   * which is exactly how it was reported.
   */
  @Test
  fun appleCollision_explainsBeforeRePrompting() = runTest(dispatcher) {
    coEvery { authManager.upgradeAnonymousAccount(AuthProvider.Apple) } returns
      AccountUpgradeResult.ReauthRequiredToMerge(AuthProvider.Apple)
    val vm = viewModel()

    vm.startUpgrade(AuthProvider.Apple)
    advanceUntilIdle()

    val state = vm.state.value as UpgradeUiState.ConfirmMerge
    assertThat(state.provider).isEqualTo(AuthProvider.Apple)
    assertThat(state.needsReauthorization).isTrue()
    // The second Apple sheet is what mergeIntoExistingAccount presents — not yet.
    coVerify(exactly = 0) { authManager.mergeIntoExistingAccount(any()) }
  }

  @Test
  fun confirmingAnAppleMerge_reauthorizesRatherThanReplaying() = runTest(dispatcher) {
    coEvery { authManager.upgradeAnonymousAccount(AuthProvider.Apple) } returns
      AccountUpgradeResult.ReauthRequiredToMerge(AuthProvider.Apple)
    coEvery { authManager.mergeIntoExistingAccount(AuthProvider.Apple) } returns
      AccountUpgradeResult.Linked(guest)
    val vm = viewModel()
    vm.startUpgrade(AuthProvider.Apple)
    advanceUntilIdle()

    vm.confirmMerge()
    advanceUntilIdle()

    coVerify { authManager.mergeIntoExistingAccount(AuthProvider.Apple) }
    // Replaying the spent credential is what produced ERROR_MISSING_OR_INVALID_NONCE.
    coVerify(exactly = 0) { authManager.signInToExistingAccount(any()) }
  }

  @Test
  fun cancellingTheMerge_leavesTheGuestAlone() = runTest(dispatcher) {
    coEvery { authManager.upgradeAnonymousAccount(AuthProvider.Apple) } returns
      AccountUpgradeResult.ReauthRequiredToMerge(AuthProvider.Apple)
    val vm = viewModel()
    vm.startUpgrade(AuthProvider.Apple)
    advanceUntilIdle()

    vm.cancel()
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(UpgradeUiState.Idle)
    coVerify(exactly = 0) { authManager.mergeIntoExistingAccount(any()) }
    coVerify(exactly = 0) { migrator.reassign(any(), any()) }
  }

  @Test
  fun incomingLink_asksBeforeLinking() = runTest(dispatcher) {
    coEvery { emailStore.pendingEmail(GUEST_UID) } returns EMAIL
    val vm = viewModel()

    EmailLinkDeepLinks.deliver(LINK)
    vm.onIncomingLink(LINK)
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(UpgradeUiState.ConfirmLink(EMAIL, LINK))
    // Nothing irreversible has happened yet — that is the whole point of the gate.
    coVerify(exactly = 0) { authManager.completeUpgradeWithEmailLink(any(), any()) }
  }

  @Test
  fun incomingLink_isLeftForTheSignInFlowWhenTheUserIsNotAGuest() = runTest(dispatcher) {
    val permanent: FirebaseUser = mockk {
      every { uid } returns "permanent-1"
      every { isAnonymous } returns false
    }
    every { authManager.getCurrentUser() } returns permanent
    coEvery { emailStore.pendingEmail(any()) } returns EMAIL
    val vm = viewModel()

    EmailLinkDeepLinks.deliver(LINK)
    vm.onIncomingLink(LINK)
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(UpgradeUiState.Idle)
    // Left unconsumed so AuthFlow can still act on it.
    assertThat(EmailLinkDeepLinks.pendingLink.value).isEqualTo(LINK)
  }

  @Test
  fun incomingLink_isIgnoredWhenThisSessionNeverRequestedOne() = runTest(dispatcher) {
    coEvery { emailStore.pendingEmail(GUEST_UID) } returns null
    val vm = viewModel()

    EmailLinkDeepLinks.deliver(LINK)
    vm.onIncomingLink(LINK)
    advanceUntilIdle()

    assertThat(vm.state.value).isEqualTo(UpgradeUiState.Idle)
  }

  @Test
  fun confirmEmailLink_linksRatherThanSigningIn() = runTest(dispatcher) {
    coEvery { emailStore.pendingEmail(GUEST_UID) } returns EMAIL
    coEvery { authManager.completeUpgradeWithEmailLink(EMAIL, LINK) } returns
      AccountUpgradeResult.Linked(guest)
    val vm = viewModel()
    vm.onIncomingLink(LINK)
    advanceUntilIdle()

    vm.confirmEmailLink()
    advanceUntilIdle()

    coVerify { authManager.completeUpgradeWithEmailLink(EMAIL, LINK) }
    // completeSignInLink would swap the current user and orphan the guest's rows.
    coVerify(exactly = 0) { authManager.completeSignInLink(any(), any()) }
  }
}
