package dev.fanfly.wingslog.feature.sharing.update

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.RedeemOutcome
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.RedeemUiState
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
private const val SECRET = "secret-redeem-1"
private const val SHARE_URL = "https://squawkit.fanfly.dev/share#$AC_ID.$SECRET"

@OptIn(ExperimentalCoroutinesApi::class)
class RedeemViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var sharing: SharingManager
  private lateinit var auth: FirebaseAuth
  private val authState = MutableStateFlow<FirebaseUser?>(null)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    sharing = mockk()
    auth = mockk()
    every { auth.authStateChanged } returns authState
    // Every redeemer publishes their technician mirror into the share they just joined (§7.2).
    coEvery { sharing.publishTechnicianMirror(any()) } returns Result.success(Unit)
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

  private fun viewModel() = RedeemViewModel(sharingManager = sharing, auth = auth)

  @Test
  fun noPendingInvite_isHidden() = runTest {
    assertThat(viewModel().uiState.value).isEqualTo(RedeemUiState.Hidden)
  }

  @Test
  fun pendingInvite_signedIn_showsConfirm() = runTest {
    authState.value = user(anonymous = false)
    every { auth.currentUser } returns authState.value
    AircraftShareDeepLinks.deliver(SHARE_URL)

    assertThat(viewModel().uiState.value).isEqualTo(RedeemUiState.Confirm)
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
    coEvery { sharing.redeemInvite(AC_ID, SECRET) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host", role = ShareRole.TECHNICIAN),
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
    coEvery { sharing.redeemInvite(AC_ID, SECRET) } returns Result.success(
      RedeemOutcome(aircraftId = AC_ID, hostUid = "host", role = ShareRole.TECHNICIAN),
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
    coEvery { sharing.redeemInvite(AC_ID, SECRET) } returns
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
    coEvery { sharing.redeemInvite(AC_ID, SECRET) } returns Result.success(
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
    coEvery { sharing.redeemInvite(AC_ID, SECRET) } returns
      Result.failure(RuntimeException("expired"))

    val vm = viewModel()
    vm.accept()

    assertThat(vm.uiState.value).isInstanceOf(RedeemUiState.Failed::class.java)
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
}
