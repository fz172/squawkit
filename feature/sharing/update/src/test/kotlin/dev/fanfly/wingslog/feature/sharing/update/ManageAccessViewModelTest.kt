package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
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
  private val role = MutableStateFlow<ShareRole?>(ShareRole.OWNER)
  private val share = MutableStateFlow(AircraftShareState())

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    sharing = mockk()
    every { sharing.observeMyRole(AC_ID) } returns role
    every { sharing.observeShareState(AC_ID) } returns share
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = ManageAccessViewModel(
    sharingManager = sharing,
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
}
