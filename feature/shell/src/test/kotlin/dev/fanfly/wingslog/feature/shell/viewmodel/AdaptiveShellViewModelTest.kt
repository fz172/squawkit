package dev.fanfly.wingslog.feature.shell.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.picker.data.SelectedAircraftStore
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import dev.gitlive.firebase.auth.AuthCredential
import io.mockk.every
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
    override suspend fun ensureSelfProfile() =
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
    override suspend fun updateDisplayName(name: String) = Unit
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

  // The engine only feeds the discarded-changes notice here; irrelevant to these assertions.
  private val syncEngine: SyncEngine = mockk(relaxed = true)

  // Drives the owned-aircraft gate; null = unlimited (default-open, capability off).
  private val aircraftLimit = MutableStateFlow<Int?>(null)
  private val subscriptionManager: SubscriptionManager = mockk {
    every { aircraftLimit() } returns aircraftLimit
  }

  // In-memory device-local selection store; starts empty so tests behave like a fresh install
  // unless they seed [selectedAircraftStore.saved].
  private val selectedAircraftStore = object : SelectedAircraftStore {
    var saved: String? = null
    override fun load(): String? = saved
    override fun save(aircraftId: String?) {
      saved = aircraftId
    }
  }

  private fun viewModel() = AdaptiveShellViewModel(
    fleetManager = fleetManager,
    technicianManager = technicianManager,
    authManager = authManager,
    sharingManager = sharingManager,
    subscriptionManager = subscriptionManager,
    syncEngine = syncEngine,
    selectedAircraftStore = selectedAircraftStore,
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
  fun restoresRememberedAircraftFromStore() = runTest(testDispatcher) {
    // Simulate a previous session that left "a2" selected on this device.
    selectedAircraftStore.saved = "a2"
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = viewModel()

    assertThat(vm.uiState.value.selectedAircraftId).isEqualTo("a2")
  }

  @Test
  fun fallsBackToFirstWhenRememberedAircraftIsGone() = runTest(testDispatcher) {
    // The remembered aircraft was deleted since last session.
    selectedAircraftStore.saved = "deleted"
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = viewModel()

    assertThat(vm.uiState.value.selectedAircraftId).isEqualTo("a1")
    // The effective selection is written back so the stale id doesn't linger.
    assertThat(selectedAircraftStore.saved).isEqualTo("a1")
  }

  @Test
  fun selectAircraftPersistsChoice() = runTest(testDispatcher) {
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = viewModel()

    vm.selectAircraft("a2")

    assertThat(selectedAircraftStore.saved).isEqualTo("a2")
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
  fun notAtLimitWhenAircraftLimitIsUnlimited() = runTest(testDispatcher) {
    aircraftLimit.value = null
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = viewModel()

    assertThat(vm.atAircraftLimit.value).isFalse()
  }

  @Test
  fun atLimitWhenOwnedCountReachesLimit() = runTest(testDispatcher) {
    aircraftLimit.value = 1
    fleet.value = listOf(aircraft("a1", "N1"))
    val vm = viewModel()

    assertThat(vm.atAircraftLimit.value).isTrue()
  }

  @Test
  fun sharedAircraftDoNotCountAgainstTheLimit() = runTest(testDispatcher) {
    aircraftLimit.value = 2
    // One owned + one shared against a limit of 2: only the owned one counts, so still under limit.
    // If the shared pointer counted, 2 >= 2 would trip the gate.
    fleet.value = listOf(aircraft("a1", "N1", shared = false), aircraft("a2", "N2", shared = true))
    val vm = viewModel()

    assertThat(vm.atAircraftLimit.value).isFalse()
  }

  @Test
  fun observesSelfProfileForSidebarAccountEntry() = runTest(testDispatcher) {
    val vm = viewModel()

    self.value = Technician(id = "self", name = "Avery Park")

    assertThat(vm.uiState.value.accountName).isEqualTo("Avery Park")
  }
}
