package dev.fanfly.wingslog.feature.logs.update.logs.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.NoOpAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.canonical.CanonicalTemplates
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceOverview
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterReading
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.Technician
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

private const val TEST_THING_ID = "thing-456"
private const val TEST_LOG_ID = "log-123"
private const val ADDRESSED_SQUAWK_ID = "squawk-addressed-by-this-log"
private const val OTHER_LOG_SQUAWK_ID = "squawk-addressed-by-other-log"
private const val OPEN_SQUAWK_ID = "squawk-open"
private const val PRESELECTED_SQUAWK_ID = "squawk-preselected"
private const val PRESELECTED_CARD_ID = "task-preselected"
private const val SELF_TECH_ID = "tech-self"
private const val TEST_UID = "uid-sponge"
private const val LINKED_UID = "uid-linked-mechanic"

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceLogFormViewModelTest {

  /**
   * A real holder over the baked-in registry, not a relaxed mock.
   *
   * The form reads `template.value.meters` to know which meter fields to render (#730), and a
   * relaxed mock answers a `StateFlow<ThingTemplate?>` with a bare Object — which casts fine until
   * something dereferences it, then throws inside a coroutine. A real holder is also the truer
   * fixture: these tests care what the form does with an airplane's three meters.
   */
  private fun airplaneTemplateHolder() = CurrentThingTemplate(
    templateRegistry,
  ).apply { set(AirplaneTemplate.TEMPLATE) }

  /** A preset with no components at all — the one that finds aviation assumptions (#732). */
  private fun homeTemplateHolder() = CurrentThingTemplate(
    templateRegistry,
  ).apply { set(CanonicalTemplates.HOME) }

  /** Two meters in different units — miles and hours — so one cannot follow the other. */
  private fun bikeTemplateHolder() = CurrentThingTemplate(
    templateRegistry,
  ).apply { set(CanonicalTemplates.BIKE) }

  /** Real, not mocked: the picker's certification labels come out of the baked-in preset pool. */
  private val templateRegistry = BakedInTemplateRegistry(appVersionCode = 1)

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var logManager: MaintenanceLogManager
  private lateinit var fleetManager: FleetManager
  private lateinit var inspectionDataManager: TaskDataManager
  private lateinit var squawkManager: SquawkManager
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var technicianManager: TechnicianManager
  private lateinit var sharingManager: SharingManager
  private lateinit var auth: FirebaseAuth
  private lateinit var subscriptionManager: SubscriptionManager

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
    subscriptionManager = mockk(relaxed = true)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.isAnonymous } returns false
    every { mockUser.uid } returns TEST_UID
    every { auth.currentUser } returns mockUser

    // Prevent the init-block flows from suspending forever.
    every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
    // Own thing by default; foreign-hosted tests override this.
    every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(false)
    every { fleetManager.loadThing(TEST_THING_ID) } returns flowOf(null)
    every { inspectionDataManager.observeTasks(TEST_THING_ID) } returns flowOf(
      emptyList()
    )
    every { technicianManager.observeTechnicians() } returns flowOf(emptyList())
    every { technicianManager.observeSelfId() } returns flowOf(null)
    every { sharingManager.observeLinkedTechnicians(TEST_THING_ID) } returns flowOf(
      emptyList()
    )
    every { logManager.observeLogs(TEST_THING_ID) } returns flowOf(emptyList())
    // Nothing recorded yet; the meter-prefill tests below supply their own overview.
    every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
      null
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun attachAvailable_onForeignHostedThing_evenWithoutOwnEntitlement() =
    runTest(testDispatcher) {
      // The host pays and the broker enforces the host's entitlement, so a member with no
      // subscription of their own can still attach on a paid owner's thing (P8.7 §9.7).
      every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
      every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(true)

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.attachmentUploadEnabled).isTrue()
    }

  @Test
  fun attachStaysOff_onOwnThing_whenTheEntitlementIsOff() =
    runTest(testDispatcher) {
      every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
      every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(
        false
      )

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.attachmentUploadEnabled).isFalse()
    }

  @Test
  fun observeSquawks_editingLog_includesSquawkAddressedByThisLog() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
        listOf(
          Squawk(
            id = ADDRESSED_SQUAWK_ID,
            title = "Nose wheel shimmy",
            addressed_by_log_id = TEST_LOG_ID,
          ),
        )
      )
      every { logManager.observeLogs(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
        subscriptionManager = subscriptionManager,
        currentThingTemplate = airplaneTemplateHolder(),
        templateRegistry = templateRegistry,
        analytics = NoOpAnalyticsManager,
        savedStateHandle = SavedStateHandle(
          mapOf(
            Screen.THING_ID to TEST_THING_ID,
            Screen.LOG_ID to TEST_LOG_ID,
            Screen.SQUAWK_ID to PRESELECTED_SQUAWK_ID,
          )
        ),
      )
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedSquawkIds).isEmpty()
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isNull()
    }

  // ---- task preselect (opened via task edit screen's "Create Work Log" resolve option) ----

  @Test
  fun preselectedCardId_seedsSelectedInspectionIds() =
    runTest(testDispatcher) {
      every { inspectionDataManager.observeTasks(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceTask(id = PRESELECTED_CARD_ID, title = "Oil change"),
        )
      )

      val viewModel =
        buildViewModelForNew(preselectedCardId = PRESELECTED_CARD_ID)
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedInspectionIds).containsExactly(
        PRESELECTED_CARD_ID
      )
      assertThat(viewModel.uiState.value.pendingResolveTaskTitle).isEqualTo("Oil change")
    }

  @Test
  fun preselectedCardId_whenEditingExistingLog_isIgnored() =
    runTest(testDispatcher) {
      every { inspectionDataManager.observeTasks(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceTask(id = PRESELECTED_CARD_ID, title = "Oil change"),
        )
      )

      // Edit-mode routes never carry a cardId nav arg, but guard the case anyway.
      val viewModel = MaintenanceLogFormViewModel(
        logManager = logManager,
        fleetManager = fleetManager,
        inspectionDataManager = inspectionDataManager,
        squawkManager = squawkManager,
        attachmentManager = attachmentManager,
        technicianManager = technicianManager,
        sharingManager = sharingManager,
        auth = auth,
        subscriptionManager = subscriptionManager,
        currentThingTemplate = airplaneTemplateHolder(),
        templateRegistry = templateRegistry,
        analytics = NoOpAnalyticsManager,
        savedStateHandle = SavedStateHandle(
          mapOf(
            Screen.THING_ID to TEST_THING_ID,
            Screen.LOG_ID to TEST_LOG_ID,
            Screen.CARD_ID to PRESELECTED_CARD_ID,
          )
        ),
      )
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedInspectionIds).isEmpty()
      assertThat(viewModel.uiState.value.pendingResolveTaskTitle).isNull()
    }

  @Test
  fun consumeResolveTaskPrefill_setsWorkDescriptionAndClearsPendingTitle() =
    runTest(testDispatcher) {
      every { inspectionDataManager.observeTasks(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceTask(id = PRESELECTED_CARD_ID, title = "Oil change"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedCardId = PRESELECTED_CARD_ID)
      advanceUntilIdle()

      viewModel.consumeResolveTaskPrefill("Performed maintenance task \"Oil change\"")

      assertThat(viewModel.uiState.value.workDescription)
        .isEqualTo("Performed maintenance task \"Oil change\"")
      assertThat(viewModel.uiState.value.pendingResolveTaskTitle).isNull()
    }

  @Test
  fun consumeResolveTaskPrefill_ignoresBlankInput_doesNotClearPendingTitle() =
    runTest(testDispatcher) {
      every { inspectionDataManager.observeTasks(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceTask(id = PRESELECTED_CARD_ID, title = "Oil change"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedCardId = PRESELECTED_CARD_ID)
      advanceUntilIdle()

      viewModel.consumeResolveTaskPrefill("")

      assertThat(viewModel.uiState.value.workDescription).isEmpty()
      assertThat(viewModel.uiState.value.pendingResolveTaskTitle).isEqualTo("Oil change")
    }

  @Test
  fun consumeResolveTaskPrefill_whenUserAlreadyTyped_prependsInsteadOfOverwriting() =
    runTest(testDispatcher) {
      every { inspectionDataManager.observeTasks(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceTask(id = PRESELECTED_CARD_ID, title = "Oil change"),
        )
      )
      val viewModel =
        buildViewModelForNew(preselectedCardId = PRESELECTED_CARD_ID)
      advanceUntilIdle()
      viewModel.onWorkDescriptionChange("Already changed the oil")

      viewModel.consumeResolveTaskPrefill("Performed maintenance task \"Oil change\"")

      assertThat(viewModel.uiState.value.workDescription)
        .isEqualTo("Performed maintenance task \"Oil change\"\nAlready changed the oil")
      assertThat(viewModel.uiState.value.pendingResolveTaskTitle).isNull()
    }

  @Test
  fun consumeResolveSquawkPrefill_setsWorkDescriptionAndClearsPendingTitle() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flow {
        // Transitional/empty first emission (e.g. auth still resolving) must not
        // permanently disable the preselect.
        emit(emptyList())
        emit(
          listOf(
            Squawk(
              id = PRESELECTED_SQUAWK_ID,
              title = "Nose wheel shimmy"
            )
          )
        )
      }

      val viewModel =
        buildViewModelForNew(preselectedSquawkId = PRESELECTED_SQUAWK_ID)
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.selectedSquawkIds).containsExactly(
        PRESELECTED_SQUAWK_ID
      )
      assertThat(viewModel.uiState.value.pendingResolveSquawkTitle).isEqualTo("Nose wheel shimmy")
    }

  @Test
  fun preselectedSquawkId_hasChangesIsFalseOnceSeedingAndPrefillHaveSettled() =
    runTest(testDispatcher) {
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
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
      assertThat(state.availableTechnicians.single().source_uid).isEqualTo(
        TEST_UID
      )
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
  fun linkedTechnicians_forThisThing_areSelectableAndKeepTheirSourceUid() =
    runTest(testDispatcher) {
      every { technicianManager.observeTechnicians() } returns flowOf(
        listOf(Technician(id = SELF_TECH_ID, name = "Sponge Bob"))
      )
      every { technicianManager.observeSelfId() } returns flowOf(SELF_TECH_ID)
      every { sharingManager.observeLinkedTechnicians(TEST_THING_ID) } returns flowOf(
        listOf(
          Technician(
            id = LINKED_UID,
            name = "Linked Mechanic",
            source_uid = LINKED_UID
          )
        )
      )

      val state = buildViewModelForNew().uiState.value

      // The linked member is offered separately from the personal list, and carries the provenance
      // that a snapshot of them needs.
      assertThat(state.availableTechnicians.map { it.id }).containsExactly(
        SELF_TECH_ID
      )
      assertThat(state.linkedTechnicians.single().source_uid).isEqualTo(
        LINKED_UID
      )
      assertThat(state.selfTechnicianId).isEqualTo(SELF_TECH_ID)
    }

  @Test
  fun selectingALinkedTechnician_snapshotsTheirMirrorIntoTheLog() =
    runTest(testDispatcher) {
      val linked = Technician(
        id = LINKED_UID,
        name = "Linked Mechanic",
        source_uid = LINKED_UID
      )
      every { sharingManager.observeLinkedTechnicians(TEST_THING_ID) } returns flowOf(
        listOf(linked)
      )
      val viewModel = buildViewModelForNew()

      viewModel.onTechnicianSelect(linked)

      // Same snapshot mechanism as a local record — only the source differs (§7.3).
      val selected = viewModel.uiState.value.selectedTechnician
      assertThat(selected?.name).isEqualTo("Linked Mechanic")
      assertThat(selected?.source_uid).isEqualTo(LINKED_UID)
    }

  // ---- component type (#732: the picker only exists where ComponentType describes the thing) ----

  @Test
  fun save_onATemplateWithoutComponents_storesNoComponentType() =
    runTest(testDispatcher) {
      val saved = slot<MaintenanceLog>()
      coEvery {
        logManager.addLog(
          TEST_THING_ID,
          capture(saved)
        )
      } returns Result.success(true)

      val viewModel =
        buildViewModelForNew(templateHolder = homeTemplateHolder())
      advanceUntilIdle()
      viewModel.onWorkDescriptionChange("Replaced the water heater anode")
      viewModel.save()
      advanceUntilIdle()

      // Not COMPONENT_AIRFRAME, the form's default — that is what put an "Airframe" pill on every
      // card of a thing that has no airframe.
      assertThat(saved.captured.component_type).isEqualTo(ComponentType.COMPONENT_UNKNOWN)
      assertThat(saved.captured.component_serial).isEmpty()
    }

  @Test
  fun save_onAnAirplane_keepsTheComponentTheUserPicked() =
    runTest(testDispatcher) {
      val saved = slot<MaintenanceLog>()
      coEvery {
        logManager.addLog(
          TEST_THING_ID,
          capture(saved)
        )
      } returns Result.success(true)

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()
      viewModel.onWorkDescriptionChange("Replaced left magneto")
      viewModel.onComponentTypeChange(ComponentType.COMPONENT_ENGINE)
      viewModel.save()
      advanceUntilIdle()

      assertThat(saved.captured.component_type).isEqualTo(ComponentType.COMPONENT_ENGINE)
    }

  // ---- meter prefill (a new log starts from the current readings) ----

  @Test
  fun newLog_prefillsTheMeterFieldsFromTheCurrentReadings() =
    runTest(testDispatcher) {
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf(
          MeterKeys.AIRFRAME_HOURS to 1234.5,
          MeterKeys.ENGINE_HOURS to 800.0,
        )
      )

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      // A reading is typed as a small edit of the current number, not looked up from scratch.
      // Prop hours are absent from the overview, so the field stays empty rather than reading 0.
      assertThat(viewModel.uiState.value.meterValues).containsExactly(
        MeterKeys.AIRFRAME_HOURS, "1234.5",
        MeterKeys.ENGINE_HOURS, "800.0",
      )
    }

  @Test
  fun newLog_prefill_doesNotComeBackAfterTheUserClearsTheField() =
    runTest(testDispatcher) {
      val overview =
        MutableStateFlow(overviewOf(MeterKeys.ENGINE_HOURS to 800.0))
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns overview

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()
      viewModel.onMeterChanged(MeterKeys.ENGINE_HOURS, "")
      // A later sync writes the overview again — the prefill is a starting point, not a default
      // the form keeps reapplying over what the user did.
      overview.value = overviewOf(MeterKeys.ENGINE_HOURS to 801.0)
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.meterValues[MeterKeys.ENGINE_HOURS]).isEmpty()
    }

  @Test
  fun editingALog_showsItsOwnReadings_notTodaysCurrentOnes() =
    runTest(testDispatcher) {
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf(MeterKeys.ENGINE_HOURS to 800.0)
      )
      every { logManager.observeLogs(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceLog(
            id = TEST_LOG_ID,
            work_description = "Oil change",
            readings = listOf(
              MeterReading(
                MeterKeys.ENGINE_HOURS,
                value_ = 640.2
              )
            ),
          )
        )
      )

      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      // Dropping today's totals into a form opened to fix a typo would rewrite what the log said.
      assertThat(viewModel.uiState.value.meterValues[MeterKeys.ENGINE_HOURS]).isEqualTo(
        "640.2"
      )
    }

  @Test
  fun newLog_onATemplateWithoutMeters_prefillsNothing() =
    runTest(testDispatcher) {
      // A home declares no meters and gets no hours tab, so there is no field to prefill — and a
      // seeded value would be saved as a reading nobody was asked for.
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf(MeterKeys.ENGINE_HOURS to 800.0)
      )

      val viewModel =
        buildViewModelForNew(templateHolder = homeTemplateHolder())
      advanceUntilIdle()

      assertThat(viewModel.uiState.value.meterValues).isEmpty()
    }

  // ---- meter suggestions (a meter that follows another is offered its increment) ----

  /** The example from the request: 1.1 / 1.7 / 2.0 on the clock, airframe flown to 3.0. */
  private fun flownAirplane(): MaintenanceLogFormViewModel {
    every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
      overviewOf(
        MeterKeys.AIRFRAME_HOURS to 1.1,
        MeterKeys.ENGINE_HOURS to 1.7,
        MeterKeys.PROP_HOURS to 2.0,
      )
    )
    return buildViewModelForNew()
  }

  @Test
  fun changingTheAirframe_offersTheSameIncrementToThePropeller() =
    runTest(testDispatcher) {
      val viewModel = flownAirplane()
      advanceUntilIdle()

      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "3.0")

      // 1.9 hours flown, so the propeller turned for 1.9 hours too. The engine is deliberately
      // absent — its hours come off its own tach (airplane v13, `follows_meter_key`) — and so is
      // the airframe, which is the meter the user just typed.
      assertThat(viewModel.uiState.value.meterSuggestions).containsExactly(
        MeterKeys.PROP_HOURS, "3.9",
      )
    }

  @Test
  fun aMeterThatAlreadyReadsItsSuggestion_isNoLongerOffered() =
    runTest(testDispatcher) {
      val viewModel = flownAirplane()
      advanceUntilIdle()
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "3.0")

      // What tapping "Use 3.9" does.
      viewModel.onMeterChanged(MeterKeys.PROP_HOURS, "3.9")

      assertThat(viewModel.uiState.value.meterSuggestions).isEmpty()
    }

  @Test
  fun aFollowedMeterThatHasNotMovedForward_offersNothing() =
    runTest(testDispatcher) {
      val viewModel = flownAirplane()
      advanceUntilIdle()

      // Back to where it started, then below it. Meters run forward; neither says anything about
      // how long the engine ran.
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "1.1")
      assertThat(viewModel.uiState.value.meterSuggestions).isEmpty()
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "0.5")
      assertThat(viewModel.uiState.value.meterSuggestions).isEmpty()
    }

  @Test
  fun aHalfTypedFollowedMeter_offersNothing() =
    runTest(testDispatcher) {
      val viewModel = flownAirplane()
      advanceUntilIdle()

      // Mid-edit: the field is cleared before the new number is typed, and "" is not a reading.
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "")

      assertThat(viewModel.uiState.value.meterSuggestions).isEmpty()
    }

  @Test
  fun changingTheAirframe_offersNothingToTheEngine() =
    runTest(testDispatcher) {
      val viewModel = flownAirplane()
      advanceUntilIdle()

      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "3.0")

      // Engine hours are read off the engine tach, which runs at its own rate. A figure derived
      // from the airframe would be wrong more often than right, so none is offered at any point.
      assertThat(viewModel.uiState.value.meterSuggestions)
        .doesNotContainKey(MeterKeys.ENGINE_HOURS)
      viewModel.onMeterChanged(MeterKeys.ENGINE_HOURS, "2.0")
      assertThat(viewModel.uiState.value.meterSuggestions)
        .doesNotContainKey(MeterKeys.ENGINE_HOURS)
    }

  @Test
  fun aMeterNoLogHasEverRecorded_isOfferedTheReadingItFollows() =
    runTest(testDispatcher) {
      // The real shape of a new aeroplane: the airframe and the engine have been written down, the
      // propeller never has (its overview entry is absent, not zero). It was fitted with the
      // airframe, so it has turned for every hour the airframe flew.
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf(
          MeterKeys.AIRFRAME_HOURS to 0.7,
          MeterKeys.ENGINE_HOURS to 1.1,
        )
      )

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "3.0")

      // The airframe's own new reading, not the 2.3 it moved by — a propeller that has never been
      // recorded has not been sitting at zero while the aeroplane flew.
      assertThat(viewModel.uiState.value.meterSuggestions).containsExactly(
        MeterKeys.PROP_HOURS, "3.0",
      )
    }

  @Test
  fun aThingWithNoReadingsAtAll_offersWhatWasJustTyped() =
    runTest(testDispatcher) {
      // Nothing to prefill, so the first log's propeller reading is the airframe reading.
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf()
      )

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "12.5")

      assertThat(viewModel.uiState.value.meterSuggestions).containsExactly(
        MeterKeys.PROP_HOURS, "12.5",
      )
    }

  @Test
  fun aPresetWhereNoMeterFollowsAnother_offersNothing() =
    runTest(testDispatcher) {
      // A bike counts miles and hours, and neither is derivable from the other: fifty more miles
      // says nothing about how long it was ridden. Its template declares no follower, so the form
      // asks for both and offers neither.
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf("odometer" to 1000.0, "ride_hours" to 50.0)
      )

      val viewModel =
        buildViewModelForNew(templateHolder = bikeTemplateHolder())
      advanceUntilIdle()
      viewModel.onMeterChanged("odometer", "1050")

      assertThat(viewModel.uiState.value.meterSuggestions).isEmpty()
    }

  @Test
  fun editingALog_measuresTheIncrementFromWhatThatLogSaid() =
    runTest(testDispatcher) {
      every { logManager.observeMaintenanceOverview(TEST_THING_ID) } returns flowOf(
        overviewOf(
          MeterKeys.AIRFRAME_HOURS to 900.0,
          MeterKeys.PROP_HOURS to 900.0
        )
      )
      every { logManager.observeLogs(TEST_THING_ID) } returns flowOf(
        listOf(
          MaintenanceLog(
            id = TEST_LOG_ID,
            work_description = "Annual",
            readings = listOf(
              MeterReading(MeterKeys.AIRFRAME_HOURS, value_ = 100.0),
              MeterReading(MeterKeys.PROP_HOURS, value_ = 80.0),
            ),
          )
        )
      )

      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()
      viewModel.onMeterChanged(MeterKeys.AIRFRAME_HOURS, "102.0")

      // From this log's own 100.0, not from the 900.0 the thing reads today.
      assertThat(viewModel.uiState.value.meterSuggestions)
        .containsExactly(MeterKeys.PROP_HOURS, "82.0")
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
      subscriptionManager = subscriptionManager,
      currentThingTemplate = airplaneTemplateHolder(),
      templateRegistry = templateRegistry,
      analytics = NoOpAnalyticsManager,
      savedStateHandle = SavedStateHandle(
        mapOf(
          Screen.THING_ID to TEST_THING_ID,
          Screen.LOG_ID to TEST_LOG_ID,
        )
      ),
    )

  private fun buildViewModelForNew(
    preselectedSquawkId: String? = null,
    preselectedCardId: String? = null,
    templateHolder: CurrentThingTemplate = airplaneTemplateHolder(),
  ): MaintenanceLogFormViewModel =
    MaintenanceLogFormViewModel(
      logManager = logManager,
      fleetManager = fleetManager,
      inspectionDataManager = inspectionDataManager,
      squawkManager = squawkManager,
      attachmentManager = attachmentManager,
      technicianManager = technicianManager,
      sharingManager = sharingManager,
      auth = auth,
      subscriptionManager = subscriptionManager,
      currentThingTemplate = templateHolder,
      templateRegistry = templateRegistry,
      analytics = NoOpAnalyticsManager,
      savedStateHandle = SavedStateHandle(
        buildMap {
          put(Screen.THING_ID, TEST_THING_ID)
          if (preselectedSquawkId != null) put(
            Screen.SQUAWK_ID,
            preselectedSquawkId
          )
          if (preselectedCardId != null) put(
            Screen.CARD_ID,
            preselectedCardId
          )
        }
      ),
    )

  private fun overviewOf(vararg readings: Pair<String, Double>) =
    MaintenanceOverview(
      aircraft_id = TEST_THING_ID,
      current = readings.map { (key, value) ->
        MeterReading(
          key,
          value_ = value
        )
      },
    )
}
