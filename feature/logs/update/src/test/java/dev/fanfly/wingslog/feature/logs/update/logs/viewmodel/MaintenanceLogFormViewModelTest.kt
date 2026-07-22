package dev.fanfly.wingslog.feature.logs.update.logs.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TEST_AIRCRAFT_ID = "aircraft-456"
private const val TEST_LOG_ID = "log-123"
private const val ADDRESSED_SQUAWK_ID = "squawk-addressed-by-this-log"
private const val OTHER_LOG_SQUAWK_ID = "squawk-addressed-by-other-log"
private const val OPEN_SQUAWK_ID = "squawk-open"
private const val PRESELECTED_SQUAWK_ID = "squawk-preselected"
private const val SELF_TECH_ID = "tech-self"
private const val TEST_UID = "uid-sponge"
private const val LINKED_UID = "uid-linked-mechanic"

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceLogFormViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var logManager: MaintenanceLogManager
  private lateinit var fleetManager: FleetManager
  private lateinit var inspectionDataManager: TaskDataManager
  private lateinit var squawkManager: SquawkManager
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var technicianManager: TechnicianManager
  private lateinit var sharingManager: SharingManager
  private lateinit var auth: FirebaseAuth
  private lateinit var featureLabManager: DeveloperOptionsManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    logManager = mockk(relaxed = true)
    fleetManager = mockk(relaxed = true)
    inspectionDataManager = mockk(relaxed = true)
    squawkManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    technicianManager = mockk(relaxed = true)
    sharingManager = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    featureLabManager = mockk(relaxed = true)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.isAnonymous } returns false
    every { mockUser.uid } returns TEST_UID
    every { auth.currentUser } returns mockUser

    // Prevent the init-block flows from suspending forever.
    every { featureLabManager.observe() } returns flowOf(DeveloperFlags())
    every { fleetManager.loadAircraft(TEST_AIRCRAFT_ID) } returns flowOf(null)
    every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(
      emptyList()
    )
    every { technicianManager.observeTechnicians() } returns flowOf(emptyList())
    every { technicianManager.observeSelfId() } returns flowOf(null)
    every { sharingManager.observeLinkedTechnicians(TEST_AIRCRAFT_ID) } returns flowOf(emptyList())
    every { logManager.observeLogs(TEST_AIRCRAFT_ID) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun observeSquawks_editingLog_includesSquawkAddressedByThisLog() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(
            id = ADDRESSED_SQUAWK_ID,
            title = "Nose wheel shimmy",
            addressed_by_log_id = TEST_LOG_ID,
          ),
        )
      )
      every { logManager.observeLogs(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          MaintenanceLog(
            id = TEST_LOG_ID,
            work_description = "Fixed shimmy",
            squawk_ids = listOf(ADDRESSED_SQUAWK_ID),
          )
        )
      )

      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      // The squawk this log addresses must remain resolvable so its title can be
      // displayed instead of falling back to the raw id.
      val resolved = viewModel.uiState.value.availableSquawks
        .firstOrNull { it.id == ADDRESSED_SQUAWK_ID }
      assertThat(resolved?.title).isEqualTo("Nose wheel shimmy")
    }

  @Test
  fun observeSquawks_editingLog_excludesSquawkAddressedByAnotherLog() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(
            id = OTHER_LOG_SQUAWK_ID,
            title = "Oil leak",
            addressed_by_log_id = "some-other-log",
          ),
        )
      )

      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      assertThat(
        viewModel.uiState.value.availableSquawks.map { it.id }
      ).doesNotContain(OTHER_LOG_SQUAWK_ID)
    }

  @Test
  fun observeSquawks_editingLog_includesUnaddressedSquawks() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = OPEN_SQUAWK_ID, title = "Flat tire"),
        )
      )

      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      assertThat(
        viewModel.uiState.value.availableSquawks.map { it.id }
      ).contains(OPEN_SQUAWK_ID)
    }

  @Test
  fun observeSquawks_newLog_excludesAlreadyAddressedSquawks() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(
            id = ADDRESSED_SQUAWK_ID,
            title = "Nose wheel shimmy",
            addressed_by_log_id = "some-log",
          ),
          Squawk(id = OPEN_SQUAWK_ID, title = "Flat tire"),
        )
      )

      // A brand-new log has no id yet, so nothing should match addressed_by_log_id.
      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(
        viewModel.uiState.value.availableSquawks.map { it.id }
      ).containsExactly(OPEN_SQUAWK_ID)
    }

  // ---- squawk preselect (opened via squawk edit screen's "Fixed" option) ----

  @Test
  fun preselectedSquawkId_seedsSelectedSquawkIdsAndPendingTitle() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy"),
        )
      )

      val viewModel =
        buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedSquawkIds).containsExactly(
        PRESELECTED_SQUAWK_ID
      )
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isEqualTo("Nose wheel shimmy")
    }

  @Test
  fun preselectedSquawkId_whenEditingExistingLog_isIgnored() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy"),
        )
      )

      // Edit-mode routes never carry a squawkId nav arg, but guard the case anyway.
      val viewModel = MaintenanceLogFormViewModel(
        logManager = logManager,
        fleetManager = fleetManager,
        inspectionDataManager = inspectionDataManager,
        squawkManager = squawkManager,
        attachmentManager = attachmentManager,
        technicianManager = technicianManager,
        sharingManager = sharingManager,
        auth = auth,
        featureLabManager = featureLabManager,
        savedStateHandle = SavedStateHandle(
          mapOf(
            Screen.AIRCRAFT_ID to TEST_AIRCRAFT_ID,
            Screen.LOG_ID to TEST_LOG_ID,
            Screen.SQUAWK_ID to PRESELECTED_SQUAWK_ID,
          )
        ),
      )
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedSquawkIds).isEmpty()
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isNull()
    }

  @Test
  fun consumeResolveSquawkPrefill_setsWorkDescriptionAndClearsPendingTitle() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()

      viewModel.consumeResolveSquawkPrefill("Resolve squawk \"Nose wheel shimmy\"")

      assertThat(viewModel.uiState.value.workDescription)
        .isEqualTo("Resolve squawk \"Nose wheel shimmy\"")
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isNull()
    }

  @Test
  fun consumeResolveSquawkPrefill_ignoresBlankInput_doesNotClearPendingTitle() =
    runTest(testDispatcher) {
      // On web, stringResource resolves the format string asynchronously and composes with
      // an empty default first — that transient "" must not permanently drop the real value.
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()

      viewModel.consumeResolveSquawkPrefill("")

      assertThat(viewModel.uiState.value.workDescription).isEmpty()
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isEqualTo("Nose wheel shimmy")
    }

  @Test
  fun consumeResolveSquawkPrefill_whenUserAlreadyTyped_prependsInsteadOfOverwriting() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()
      // User starts typing their own description before the prefill round-trip completes.
      viewModel.onWorkDescriptionChange("Already replaced the bulb")

      viewModel.consumeResolveSquawkPrefill("Resolve squawk \"Nose wheel shimmy\"")

      assertThat(viewModel.uiState.value.workDescription)
        .isEqualTo("Resolve squawk \"Nose wheel shimmy\"\nAlready replaced the bulb")
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isNull()
    }

  @Test
  fun preselectedSquawkId_notInFirstEmission_stillSeedsOnceItAppearsLater() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flow {
        // Transitional/empty first emission (e.g. auth still resolving) must not
        // permanently disable the preselect.
        emit(emptyList())
        emit(listOf(Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy")))
      }

      val viewModel = buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedSquawkIds).containsExactly(PRESELECTED_SQUAWK_ID)
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isEqualTo("Nose wheel shimmy")
    }

  @Test
  fun preselectedSquawkId_hasChangesIsFalseOnceSeedingAndPrefillHaveSettled() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Squawk(id = PRESELECTED_SQUAWK_ID, title = "Nose wheel shimmy"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()

      viewModel.consumeResolveSquawkPrefill("Resolve squawk \"Nose wheel shimmy\"")

      // The auto-preselect + auto-prefill are the baseline, not user edits — hasChanges must
      // stay false until the user actually changes something themselves.
      assertThat(viewModel.uiState.value.hasChanges).isFalse()
    }

  // ---- source_uid provenance on the technician snapshot (design §7.3) ----

  @Test
  fun selfTechnician_isStampedWithSourceUid_andIsSelectedByDefault() =
    runTest(testDispatcher) {
      every { technicianManager.observeTechnicians() } returns flowOf(
        listOf(Technician(id = SELF_TECH_ID, name = "Sponge Bob"))
      )
      every { technicianManager.observeSelfId() } returns flowOf(SELF_TECH_ID)

      val state = buildViewModelForNew().uiState.value

      assertThat(state.selectedTechnician?.source_uid).isEqualTo(TEST_UID)
      assertThat(state.availableTechnicians.single().source_uid).isEqualTo(TEST_UID)
    }

  @Test
  fun manualTechnician_carriesNoSourceUid() = runTest(testDispatcher) {
    every { technicianManager.observeTechnicians() } returns flowOf(
      listOf(
        Technician(id = SELF_TECH_ID, name = "Sponge Bob"),
        Technician(id = "manual-1", name = "Hand-typed Mechanic"),
      )
    )
    every { technicianManager.observeSelfId() } returns flowOf(SELF_TECH_ID)

    val available = buildViewModelForNew().uiState.value.availableTechnicians

    // A manual entry was typed by hand, not linked to an account — provenance stays empty.
    assertThat(available.single { it.id == "manual-1" }.source_uid).isEmpty()
  }

  @Test
  fun linkedTechnicians_forThisAircraft_areSelectableAndKeepTheirSourceUid() =
    runTest(testDispatcher) {
      every { technicianManager.observeTechnicians() } returns flowOf(
        listOf(Technician(id = SELF_TECH_ID, name = "Sponge Bob"))
      )
      every { technicianManager.observeSelfId() } returns flowOf(SELF_TECH_ID)
      every { sharingManager.observeLinkedTechnicians(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(
          Technician(id = LINKED_UID, name = "Linked Mechanic", source_uid = LINKED_UID)
        )
      )

      val state = buildViewModelForNew().uiState.value

      // The linked member is offered separately from the personal list, and carries the provenance
      // that a snapshot of them needs.
      assertThat(state.availableTechnicians.map { it.id }).containsExactly(SELF_TECH_ID)
      assertThat(state.linkedTechnicians.single().source_uid).isEqualTo(LINKED_UID)
      assertThat(state.selfTechnicianId).isEqualTo(SELF_TECH_ID)
    }

  @Test
  fun selectingALinkedTechnician_snapshotsTheirMirrorIntoTheLog() = runTest(testDispatcher) {
    val linked = Technician(id = LINKED_UID, name = "Linked Mechanic", source_uid = LINKED_UID)
    every { sharingManager.observeLinkedTechnicians(TEST_AIRCRAFT_ID) } returns flowOf(listOf(linked))
    val viewModel = buildViewModelForNew()

    viewModel.onTechnicianSelect(linked)

    // Same snapshot mechanism as a local record — only the source differs (§7.3).
    val selected = viewModel.uiState.value.selectedTechnician
    assertThat(selected?.name).isEqualTo("Linked Mechanic")
    assertThat(selected?.source_uid).isEqualTo(LINKED_UID)
  }

  // ---- helpers ----

  private fun buildViewModelForEdit(): MaintenanceLogFormViewModel =
    MaintenanceLogFormViewModel(
      logManager = logManager,
      fleetManager = fleetManager,
      inspectionDataManager = inspectionDataManager,
      squawkManager = squawkManager,
      attachmentManager = attachmentManager,
      technicianManager = technicianManager,
      sharingManager = sharingManager,
      auth = auth,
      featureLabManager = featureLabManager,
      savedStateHandle = SavedStateHandle(
        mapOf(
          Screen.AIRCRAFT_ID to TEST_AIRCRAFT_ID,
          Screen.LOG_ID to TEST_LOG_ID,
        )
      ),
    )

  private fun buildViewModelForNew(preselectedSquawkId: String? = null): MaintenanceLogFormViewModel =
    MaintenanceLogFormViewModel(
      logManager = logManager,
      fleetManager = fleetManager,
      inspectionDataManager = inspectionDataManager,
      squawkManager = squawkManager,
      attachmentManager = attachmentManager,
      technicianManager = technicianManager,
      sharingManager = sharingManager,
      auth = auth,
      featureLabManager = featureLabManager,
      savedStateHandle = SavedStateHandle(
        buildMap {
          put(Screen.AIRCRAFT_ID, TEST_AIRCRAFT_ID)
          if (preselectedSquawkId != null) put(
            Screen.SQUAWK_ID,
            preselectedSquawkId
          )
        }
      ),
    )
}
