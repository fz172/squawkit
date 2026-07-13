package dev.fanfly.wingslog.feature.sharing.update

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.InviteLink
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
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

private const val AC_ID = "ac-invite-001"

@OptIn(ExperimentalCoroutinesApi::class)
class InviteSheetViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var sharing: SharingManager
  private val share = MutableStateFlow(AircraftShareState())

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    sharing = mockk()
    every { sharing.observeShareState(AC_ID) } returns share
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  // Supplies the aircraft label carried on the invite for the invitee's preview (#201).
  private val fleet: FleetManager = mockk(relaxed = true)

  private fun viewModel() = InviteSheetViewModel(
    sharingManager = sharing,
    fleetManager = fleet,
    savedStateHandle = SavedStateHandle(mapOf(Screen.AIRCRAFT_ID to AC_ID)),
  )

  @Test
  fun defaultRole_isTechnician() = runTest {
    assertThat(viewModel().uiState.value.selectedRole).isEqualTo(ShareRole.TECHNICIAN)
  }

  @Test
  fun selectRole_updatesState() = runTest {
    val vm = viewModel()
    vm.selectRole(ShareRole.OWNER)
    assertThat(vm.uiState.value.selectedRole).isEqualTo(ShareRole.OWNER)
  }

  @Test
  fun createInvite_success_autoExpandsNewInvite() = runTest {
    coEvery { sharing.createInvite(AC_ID, ShareRole.OWNER, any()) } returns
      Result.success(
        InviteLink(
          code = "EFA2GGTH",
          formattedCode = "EFA2-GGTH",
          codeId = "h",
          url = "https://squawkit.fanfly.dev/share#EFA2GGTH",
          expiresAtEpochMs = 0L,
        ),
      )

    val vm = viewModel()
    vm.selectRole(ShareRole.OWNER)
    vm.createInvite()

    assertThat(vm.uiState.value.expandedToken).isEqualTo("h")
    assertThat(vm.uiState.value.creating).isFalse()
    coVerify { sharing.createInvite(AC_ID, ShareRole.OWNER, any()) }
  }

  @Test
  fun createInvite_failure_surfacesErrorAndClearsCreating() = runTest {
    coEvery { sharing.createInvite(AC_ID, ShareRole.TECHNICIAN, any()) } returns
      Result.failure(RuntimeException("no network"))

    val vm = viewModel()
    vm.createInvite()

    assertThat(vm.uiState.value.expandedToken).isNull()
    assertThat(vm.uiState.value.creating).isFalse()
    assertThat(vm.uiState.value.error).isEqualTo("no network")
  }

  @Test
  fun toggleExpand_togglesExpandedToken() = runTest {
    val vm = viewModel()
    vm.toggleExpand("t1")
    assertThat(vm.uiState.value.expandedToken).isEqualTo("t1")
    vm.toggleExpand("t1")
    assertThat(vm.uiState.value.expandedToken).isNull()
  }

  @Test
  fun pendingInvites_reflectShareState() = runTest {
    val vm = viewModel()
    share.value = AircraftShareState(
      invites = listOf(
        PendingInvite(
          codeId = "t1",
          role = ShareRole.TECHNICIAN,
          createdAtEpochMs = 0L,
          expiresAtEpochMs = 1L,
        ),
      ),
    )

    assertThat(vm.uiState.value.pendingInvites).hasSize(1)
    assertThat(vm.uiState.value.pendingInvites.first().codeId).isEqualTo("t1")
  }

  @Test
  fun cancelInvite_delegatesToManager() = runTest {
    coEvery { sharing.cancelInvite(AC_ID, "t1") } returns Result.success(Unit)

    viewModel().cancelInvite("t1")

    coVerify { sharing.cancelInvite(AC_ID, "t1") }
  }
}
