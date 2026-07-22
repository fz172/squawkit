package dev.fanfly.wingslog.feature.sharing.update

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.InvitePreview
import dev.fanfly.wingslog.feature.sharing.model.RedeemOutcome
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.RedeemUiState
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val AC_ID = "ac-redeem-1"
private const val CODE = "EFA2GGTH"
private const val SHARE_URL = "https://squawkit.fanfly.dev/share#$CODE"

@OptIn(ExperimentalCoroutinesApi::class)
class RedeemViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var sharing: SharingManager
  private lateinit var auth: FirebaseAuth
  private val authState = MutableStateFlow<FirebaseUser?>(null)

  // Sharing is gated on the build (#134). On for these tests unless a test says otherwise.
  private var capability = appCapability(sharing = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    sharing = mockk()
    auth = mockk()
    every { auth.authStateChanged } returns authState
    // Every redeemer publishes their technician mirror into the share they just joined (§7.2).
    coEvery { sharing.publishTechnicianMirror(any()) } returns Result.success(Unit)
    // The sheet previews what you are joining (#201); irrelevant to these assertions unless stated.
    coEvery { sharing.previewInvite(any()) } returns Result.failure(RuntimeException("no preview"))
    // Default redeem answer. These tests share one global invite channel + auth flow, and a VM from
    // a prior test is never cancelled, so parking an auto-accept code (#209) wakes those stale VMs
    // too. A default keeps them harmless; a test's own specific stub (eq(CODE)) still wins.
    coEvery { sharing.redeemInvite(any()) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host-uid", role = ShareRole.TECHNICIAN),
    )
    AircraftShareDeepLinks.consume()
  }

  @After
  fun tearDown() {
    AircraftShareDeepLinks.consume()
    Dispatchers.resetMain()
  }

  private fun user(anonymous: Boolean): FirebaseUser = mockk {
    every { isAnonymous } returns anonymous
  }

  private fun viewModel() = RedeemViewModel(sharingManager = sharing, auth = auth, appCapability = capability)

  @Test
  fun noPendingInvite_isHidden() = runTest {
    assertThat(viewModel().uiState.value).isEqualTo(RedeemUiState.Hidden)
  }

  @Test
  fun pendingInvite_signedIn_showsConfirm() = runTest {
    authState.value = user(anonymous = false)
    every { auth.currentUser } returns authState.value
    AircraftShareDeepLinks.deliver(SHARE_URL)

    assertThat(viewModel().uiState.value).isInstanceOf(RedeemUiState.Confirm::class.java)
  }

  @Test
  fun pendingInvite_anonymous_needsSignIn() = runTest {
    authState.value = user(anonymous = true)
    AircraftShareDeepLinks.deliver(SHARE_URL)

    assertThat(viewModel().uiState.value).isEqualTo(RedeemUiState.NeedsSignIn)
  }

  @Test
  fun accept_success_showsSuccessWithRole() = runTest {
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    AircraftShareDeepLinks.deliver(SHARE_URL)
    coEvery { sharing.redeemInvite(CODE) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host-uid", role = ShareRole.TECHNICIAN),
    )

    val vm = viewModel()
    vm.accept()

    assertThat(vm.uiState.value).isEqualTo(RedeemUiState.Success(ShareRole.TECHNICIAN))
  }

  @Test
  fun accept_success_publishesTechnicianMirror() = runTest {
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    AircraftShareDeepLinks.deliver(SHARE_URL)
    coEvery { sharing.redeemInvite(CODE) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host-uid", role = ShareRole.TECHNICIAN),
    )

    viewModel().accept()

    // Named explicitly, not left to local membership: the ref for the share we just joined is still
    // syncing down, so a publish without it would skip this aircraft and leave the auth-token name.
    coVerify { sharing.publishTechnicianMirror(alsoPublishTo = AC_ID) }
  }

  @Test
  fun accept_failure_doesNotPublishTechnicianMirror() = runTest {
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    AircraftShareDeepLinks.deliver(SHARE_URL)
    coEvery { sharing.redeemInvite(CODE) } returns
      Result.failure(RuntimeException("expired"))

    viewModel().accept()

    coVerify(exactly = 0) { sharing.publishTechnicianMirror(any()) }
  }

  @Test
  fun accept_alreadyMember_showsAlreadyMember() = runTest {
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    AircraftShareDeepLinks.deliver(SHARE_URL)
    coEvery { sharing.redeemInvite(CODE) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host", role = ShareRole.TECHNICIAN, alreadyMember = true),
    )

    val vm = viewModel()
    vm.accept()

    assertThat(vm.uiState.value).isEqualTo(RedeemUiState.AlreadyMember)
  }

  @Test
  fun accept_failure_showsFailed() = runTest {
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    AircraftShareDeepLinks.deliver(SHARE_URL)
    coEvery { sharing.redeemInvite(CODE) } returns
      Result.failure(RuntimeException("expired"))

    val vm = viewModel()
    vm.accept()

    assertThat(vm.uiState.value).isInstanceOf(RedeemUiState.Failed::class.java)
  }

  @Test
  fun typedCode_signedIn_redeemsWithoutAskingToConfirm() = runTest {
    // #209: typing the code was the consent. A signed-in user who types one goes straight to the
    // outcome — the "you've been invited, accept?" step is skipped, never shown.
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    coEvery { sharing.redeemInvite(CODE) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host-uid", role = ShareRole.TECHNICIAN),
    )

    AircraftShareDeepLinks.deliverCode(CODE)

    assertThat(viewModel().uiState.value).isEqualTo(RedeemUiState.Success(ShareRole.TECHNICIAN))
  }

  @Test
  fun typedCode_guest_signsInThenRedeems() = runTest {
    // A guest who types a code is told to sign in; once they do, the parked auto-accept code redeems
    // itself — same park-and-resume as a link, but with no confirm step on the far side.
    authState.value = user(anonymous = true)
    coEvery { sharing.redeemInvite(CODE) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host-uid", role = ShareRole.TECHNICIAN),
    )
    AircraftShareDeepLinks.deliverCode(CODE)
    val vm = viewModel()
    assertThat(vm.uiState.value).isEqualTo(RedeemUiState.NeedsSignIn)

    val signedIn = user(anonymous = false)
    every { auth.currentUser } returns signedIn
    authState.value = signedIn

    assertThat(vm.uiState.value).isEqualTo(RedeemUiState.Success(ShareRole.TECHNICIAN))
  }

  @Test
  fun dismiss_consumesInviteAndHides() = runTest {
    authState.value = user(anonymous = false)
    every { auth.currentUser } returns authState.value
    AircraftShareDeepLinks.deliver(SHARE_URL)
    val vm = viewModel()

    vm.dismiss()

    assertThat(vm.uiState.value).isEqualTo(RedeemUiState.Hidden)
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()
  }

  @Test
  fun confirm_showsWhatYouAreJoining() = runTest {
    // Until #164 this was impossible: the invitee held an aircraft id the rules must refuse to
    // resolve for a non-member, so the sheet could only say "an aircraft" and accepting meant
    // accepting blind.
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    coEvery { sharing.previewInvite(CODE) } returns Result.success(
      InvitePreview(
        aircraftLabel = "N2037O · Cessna 172",
        hostName = "Fan Zhang",
        role = ShareRole.TECHNICIAN,
      ),
    )
    AircraftShareDeepLinks.deliver(SHARE_URL)

    val state = viewModel().uiState.value

    assertThat(state).isInstanceOf(RedeemUiState.Confirm::class.java)
    assertThat((state as RedeemUiState.Confirm).preview?.aircraftLabel)
      .isEqualTo("N2037O · Cessna 172")
    assertThat(state.preview?.hostName).isEqualTo("Fan Zhang")
  }

  @Test
  fun confirm_stillAcceptable_whenThePreviewFails() = runTest {
    // The preview exists to inform, not to gate. A failed lookup must not block Accept.
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    coEvery { sharing.previewInvite(CODE) } returns Result.failure(RuntimeException("offline"))
    AircraftShareDeepLinks.deliver(SHARE_URL)

    val state = viewModel().uiState.value

    assertThat(state).isInstanceOf(RedeemUiState.Confirm::class.java)
    assertThat((state as RedeemUiState.Confirm).preview).isNull()
  }

  @Test
  fun sharingGatedOff_theInviteLinkIsIgnoredEntirely() = runTest {
    // Off must mean off, not merely hidden. A parked invite would surface the sheet the moment the
    // gate flipped — long after the user tapped anything — so the link is consumed and dropped.
    capability = appCapability(sharing = false)
    val signedIn = user(anonymous = false)
    authState.value = signedIn
    every { auth.currentUser } returns signedIn
    AircraftShareDeepLinks.deliver(SHARE_URL)

    val vm = viewModel()

    assertThat(vm.uiState.value).isEqualTo(RedeemUiState.Hidden)
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()
  }
}

private fun appCapability(sharing: Boolean) = AppCapability(
  isDeveloperOptionsSupported = false,
  isAircraftSharingSupported = sharing,
  isStressTestSupported = false,
  isCameraCaptureSupported = false,
  isAnonymousLoginSupported = true,
  isAppleSignInSupported = false,
  isSubscriptionSupported = false,
)
