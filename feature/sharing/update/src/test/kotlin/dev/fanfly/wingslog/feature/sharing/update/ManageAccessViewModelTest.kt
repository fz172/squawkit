package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val AC_ID = "ac-manage-001"

@OptIn(ExperimentalCoroutinesApi::class)
class ManageAccessViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var sharing: SharingManager
  private lateinit var cloudSync: CloudSyncSetting
  private lateinit var subscription: SubscriptionManager
  private val role = MutableStateFlow<ShareRole?>(ShareRole.OWNER)
  private val share = MutableStateFlow(AircraftShareState())
  // Host-a-share gate; on (default-open) unless a test flips it.
  private val canHostShare = MutableStateFlow(true)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    sharing = mockk()
    // Sharing is a cloud feature (PRD E2); these tests are about the roster, so sync is on.
    cloudSync = CloudSyncSetting { true }
    subscription = mockk()
    every { sharing.observeMyRole(AC_ID) } returns role
    every { sharing.observeShareState(AC_ID) } returns share
    every { subscription.canHostShare() } returns canHostShare
    // Opening the screen republishes our own member doc, self-healing a missing one.
    coEvery { sharing.publishTechnicianMirror(any()) } returns Result.success(Unit)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = ManageAccessViewModel(
    sharingManager = sharing,
    cloudSync = cloudSync,
    subscriptionManager = subscription,
    savedStateHandle = SavedStateHandle(mapOf(Screen.AIRCRAFT_ID to AC_ID)),
  )

  @Test
  fun state_reflectsRoleAndMembers() = runTest {
    share.value = AircraftShareState(
      members = listOf(
        ShareMember(uid = "host", displayName = "Host", role = ShareRole.OWNER, isHost = true),
        ShareMember(uid = "tech", displayName = "Tech", role = ShareRole.TECHNICIAN),
      ),
    )

    val state = viewModel().uiState.value

    assertThat(state.isLoading).isFalse()
    assertThat(state.myRole).isEqualTo(ShareRole.OWNER)
    assertThat(state.canManage).isTrue()
    assertThat(state.members).hasSize(2)
  }

  @Test
  fun changeRole_delegatesToManager() = runTest {
    coEvery { sharing.updateRole(AC_ID, "tech", ShareRole.OWNER) } returns Result.success(Unit)

    viewModel().changeRole("tech", ShareRole.OWNER)

    coVerify { sharing.updateRole(AC_ID, "tech", ShareRole.OWNER) }
  }

  @Test
  fun revoke_delegatesToManager() = runTest {
    coEvery { sharing.revokeMember(AC_ID, "tech") } returns Result.success(Unit)

    viewModel().revoke("tech")

    coVerify { sharing.revokeMember(AC_ID, "tech") }
  }

  @Test
  fun leave_onSuccess_setsLeaveSuccess() = runTest {
    coEvery { sharing.leave(AC_ID) } returns Result.success(Unit)

    val vm = viewModel()
    vm.leave()

    assertThat(vm.uiState.value.leaveSuccess).isTrue()
  }

  @Test
  fun leave_onFailure_surfacesError_andDoesNotSignalLeave() = runTest {
    coEvery { sharing.leave(AC_ID) } returns Result.failure(RuntimeException("boom"))

    val vm = viewModel()
    vm.leave()

    assertThat(vm.uiState.value.leaveSuccess).isFalse()
    assertThat(vm.uiState.value.error).isEqualTo("boom")
  }

  @Test
  fun rosterUnavailable_stillResolvesRole_andCanManage() = runTest {
    // A never-shared aircraft: the owner isn't in memberRoles yet, so the roster read is denied.
    // Role is resolved locally and must still land (so the Invite action stays available); the
    // roster failure is swallowed rather than surfaced or left spinning.
    every { sharing.observeShareState(AC_ID) } returns flow { throw RuntimeException("denied") }

    val state = viewModel().uiState.value

    assertThat(state.isLoading).isFalse()
    assertThat(state.error).isNull()
    assertThat(state.myRole).isEqualTo(ShareRole.OWNER)
    assertThat(state.canManage).isTrue()
  }

  // --- Revocation while the screen is open (#143) ---

  @Test
  fun accessDeniedAfterAppearingInRoster_closesTheScreen() = runTest {
    // B is on the roster, then A revokes them: the rules cut B's roster listener the moment B leaves
    // memberRoles. Leaving the screen up would show B a roster that still lists B as a member.
    share.value = AircraftShareState(
      members = listOf(
        ShareMember(uid = "host", displayName = "Host", role = ShareRole.OWNER, isHost = true),
        ShareMember(uid = "me", displayName = "Me", role = ShareRole.TECHNICIAN, isSelf = true),
      ),
    )
    val vm = viewModel()
    assertThat(vm.uiState.value.accessRevoked).isFalse()

    share.value = AircraftShareState(accessDenied = true)

    assertThat(vm.uiState.value.accessRevoked).isTrue()
  }

  @Test
  fun accessDeniedWithoutEverAppearingInRoster_doesNotCloseTheScreen() = runTest {
    // The same PERMISSION_DENIED on the wire, but from an owner whose aircraft has no share doc yet.
    // Nothing has been revoked — there is nothing to revoke — so the screen must stay put and let
    // them invite.
    role.value = ShareRole.OWNER
    share.value = AircraftShareState(accessDenied = true)

    val vm = viewModel()

    assertThat(vm.uiState.value.accessRevoked).isFalse()
  }

  @Test
  fun accessDenied_doesNotStrandTheStaleRoster() = runTest {
    share.value = AircraftShareState(
      members = listOf(
        ShareMember(uid = "me", displayName = "Me", role = ShareRole.TECHNICIAN, isSelf = true),
      ),
    )
    val vm = viewModel()

    share.value = AircraftShareState(accessDenied = true)

    // The roster we last saw is not overwritten with an empty one (that would flash an empty screen
    // on the way out), but the screen is on its way out — that is what accessRevoked means.
    assertThat(vm.uiState.value.accessRevoked).isTrue()
    assertThat(vm.uiState.value.members).hasSize(1)
  }

  // --- Cloud Sync off (PRD E2) ---

  @Test
  fun syncOff_disablesManagementAndLeaving() = runTest {
    // Sharing is a cloud feature end to end: with sync off there is nothing to share into and
    // nothing to receive from. Offering Invite / Remove / Leave here would fail on tap.
    cloudSync = CloudSyncSetting { false }
    share.value = AircraftShareState(
      members = listOf(
        ShareMember(uid = "host", displayName = "Host", role = ShareRole.OWNER, isHost = true),
        ShareMember(uid = "me", displayName = "Me", role = ShareRole.TECHNICIAN, isSelf = true),
      ),
    )

    val state = viewModel().uiState.value

    assertThat(state.syncEnabled).isFalse()
    assertThat(state.canManage).isFalse()
    assertThat(state.canLeave).isFalse()
  }

  @Test
  fun syncOn_ownerCanManage() = runTest {
    role.value = ShareRole.OWNER
    share.value = AircraftShareState(
      members = listOf(
        ShareMember(uid = "host", displayName = "Host", role = ShareRole.OWNER, isHost = true, isSelf = true),
      ),
    )

    val state = viewModel().uiState.value

    assertThat(state.canManage).isTrue()
  }

  @Test
  fun canHostShare_defaultsOpen() = runTest {
    canHostShare.value = true

    assertThat(viewModel().uiState.value.canHostShare).isTrue()
  }

  @Test
  fun canHostShare_lockedWhenGateOff() = runTest {
    canHostShare.value = false

    // The Invite action stays visible for owners but is surfaced as a promo by the route.
    assertThat(viewModel().uiState.value.canHostShare).isFalse()
  }
}
