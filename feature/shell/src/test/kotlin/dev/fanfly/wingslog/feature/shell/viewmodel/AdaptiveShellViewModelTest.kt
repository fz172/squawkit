package dev.fanfly.wingslog.feature.shell.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import dev.gitlive.firebase.auth.AuthCredential
import io.mockk.mockk
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveShellViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private val fleet = MutableStateFlow<List<FleetEntry>>(emptyList())
  private val self = MutableStateFlow<Technician?>(null)
  private val fleetManager = object : FleetManager {
    override fun observeFleetDashboard(): Flow<List<FleetEntry>> = fleet
    override suspend fun updateAircraft(aircraft: Aircraft) =
      Result.success(true)

    override fun loadAircraft(id: String): Flow<Aircraft?> =
      MutableStateFlow(null)

    override suspend fun deleteAircraft(id: String) = Result.success(true)
  }
  private val technicianManager = object : TechnicianManager {
    override fun observeTechnicians(): Flow<List<Technician>> =
      MutableStateFlow(emptyList())

    override fun loadTechnician(id: String): Flow<Technician?> =
      MutableStateFlow(null)

    override fun observeSelf(): Flow<Technician?> = self
    override fun observeSelfId(): Flow<String?> = MutableStateFlow(null)
    override suspend fun updateTechnician(technician: Technician) =
      Result.success(true)

    override suspend fun deleteTechnician(id: String) = Result.success(true)
    override suspend fun saveSelfName(name: String) = Result.success(Unit)
    override suspend fun ensureSelfProfile(replaceExistingName: Boolean) =
      Result.success(Unit)

    override suspend fun applyDuplicateMerges(
      groups: List<DuplicateGroup>,
      reviewedSignature: String,
    ) = Result.success(Unit)

    override fun observeReviewedDuplicatesSignature(): Flow<String?> = MutableStateFlow(null)
    override suspend fun markDuplicatesReviewed(signature: String) = Result.success(Unit)
  }
  private val authManager = object : AuthManager {
    override fun getCurrentUser(): FirebaseUser? = null
    override suspend fun trySilentLogin(): FirebaseUser? = null
    override suspend fun signInWithGoogle(): FirebaseUser? = null
    override suspend fun signInAnonymously(): FirebaseUser? = null
    override suspend fun logOut() = Unit
    override suspend fun sendSignInLink(email: String): SendLinkResult =
      SendLinkResult.Failed("not used")

    override fun isSignInWithEmailLink(link: String): Boolean = false
    override suspend fun completeSignInLink(
      email: String,
      link: String
    ): FirebaseUser? = null

    override suspend fun upgradeAnonymousAccount(): AccountUpgradeResult =
      AccountUpgradeResult.Cancelled

    override suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult =
      AccountUpgradeResult.Cancelled
  }

  @Before
  fun setUp() = Dispatchers.setMain(testDispatcher)

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun aircraft(
    id: String,
    tail: String,
    make: String = "Cessna",
    model: String = "172",
    shared: Boolean = false,
  ) =
    FleetEntry(
      aircraft = Aircraft(id = id, make = make, model = model, tail_number = tail),
      shared = shared,
      role = ShareRole.SHARE_ROLE_OWNER,
    )

  // The shell republishes the technician mirror on app start (design §7.2); irrelevant to these
  // assertions, so a relaxed mock keeps it out of the way.
  private val sharingManager: SharingManager = mockk(relaxed = true)

  private fun viewModel() = AdaptiveShellViewModel(
    fleetManager = fleetManager,
    technicianManager = technicianManager,
    authManager = authManager,
    sharingManager = sharingManager,
  )

  @Test
  fun mapsFleetAndSelectsFirstByDefault() = runTest(testDispatcher) {
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = viewModel()

    val s = vm.uiState.value
    assertThat(s.aircraft.map { it.tail }).containsExactly("N1", "N2")
      .inOrder()
    assertThat(s.aircraft.first().name).isEqualTo("Cessna 172")
    assertThat(s.selectedAircraftId).isEqualTo("a1")
    assertThat(s.section).isEqualTo(ShellSection.DASHBOARD)
  }

  @Test
  fun keepsSelectionAcrossReemissionWhenStillPresent() = runTest(testDispatcher) {
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = viewModel()
    vm.selectAircraft("a2")

    // Re-emit with the same aircraft; the explicit selection must survive.
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    assertThat(vm.uiState.value.selectedAircraftId).isEqualTo("a2")

    // Remove the selected one; selection falls back to the first remaining.
    fleet.value = listOf(aircraft("a1", "N1"))
    assertThat(vm.uiState.value.selectedAircraftId).isEqualTo("a1")
  }

  @Test
  fun selectsFirstAircraftWhenFleetArrivesAfterEmptyState() = runTest(testDispatcher) {
    fleet.value = emptyList()
    val vm = viewModel()

    assertThat(vm.uiState.value.selectedAircraftId).isNull()

    fleet.value = listOf(aircraft("a1", "N1"))

    val s = vm.uiState.value
    assertThat(s.selectedAircraftId).isEqualTo("a1")
    assertThat(s.section).isEqualTo(ShellSection.DASHBOARD)
  }

  @Test
  fun selectSectionUpdatesSection() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.selectSection(ShellSection.SQUAWKS)
    assertThat(vm.uiState.value.section).isEqualTo(ShellSection.SQUAWKS)
  }

  @Test
  fun openSettingsSelectsSettingsAndEnters() = runTest(testDispatcher) {
    val vm = viewModel()

    vm.openSettings()
    val s = vm.uiState.value
    assertThat(s.section).isEqualTo(ShellSection.SETTINGS)
  }

  @Test
  fun observesSelfProfileForSidebarAccountEntry() = runTest(testDispatcher) {
    val vm = viewModel()

    self.value = Technician(id = "self", name = "Avery Park")

    assertThat(vm.uiState.value.accountName).isEqualTo("Avery Park")
  }
}
