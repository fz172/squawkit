package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.ForceCompliedStatus
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val TEST_AIRCRAFT_ID = "aircraft-456"
private const val TEST_CARD_ID = "card-789"

/**
 * Covers the #254 fix: form WIP values live in the ViewModel (not composable `remember`) so they
 * survive the form composables being torn down and re-created when the OS file picker returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var inspectionDataManager: TaskDataManager
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var auth: FirebaseAuth
  private lateinit var maintenanceLogManager: MaintenanceLogManager
  private lateinit var subscriptionManager: SubscriptionManager
  private lateinit var sharingManager: SharingManager
  private lateinit var taskDueManager: TaskDueManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    inspectionDataManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    maintenanceLogManager = mockk(relaxed = true)
    subscriptionManager = mockk(relaxed = true)
    sharingManager = mockk(relaxed = true)
    taskDueManager = mockk(relaxed = true)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.isAnonymous } returns false
    every { auth.currentUser } returns mockUser

    // Prevent the load flows from suspending forever.
    every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
    // Own thing by default; foreign-hosted tests override this.
    every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(false)
    every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(
      emptyList()
    )
    every { maintenanceLogManager.observeLogs(TEST_AIRCRAFT_ID) } returns flowOf(
      emptyList()
    )
    every { maintenanceLogManager.observeMaintenanceOverview(TEST_AIRCRAFT_ID) } returns flowOf(
      null
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ---- add mode ----

  @Test
  fun newMode_formStateStartsWithDefaults() = runTest(testDispatcher) {
    val viewModel = buildViewModelForNew()

    assertThat(viewModel.formState.value.title).isEmpty()
    assertThat(viewModel.formState.value.hasChanges).isFalse()
  }

  @Test
  fun onTitleChange_updatesFormStateAndMarksChanged() =
    runTest(testDispatcher) {
      val viewModel = buildViewModelForNew()

      viewModel.onTitleChange("Annual inspection")

      assertThat(viewModel.formState.value.title).isEqualTo("Annual inspection")
      assertThat(viewModel.formState.value.hasChanges).isTrue()
    }

  /** The #254 regression: picking a file must not wipe fields typed before it. */
  @Test
  fun addLocalFiles_doesNotClobberInProgressFormFields() =
    runTest(testDispatcher) {
      coEvery {
        attachmentManager.addPickedFile(
          any(),
          any(),
          any()
        )
      } returns mockk(relaxed = true)
      val viewModel = buildViewModelForNew()
      viewModel.onTitleChange("Annual inspection")
      viewModel.onRefNumberChange("AD-2024-01")

      viewModel.addLocalFiles(
        listOf(PickedFile("uri", "log.pdf", "application/pdf", 100L))
      )
      advanceUntilIdle()

      assertThat(viewModel.formState.value.title).isEqualTo("Annual inspection")
      assertThat(viewModel.formState.value.refNumber).isEqualTo("AD-2024-01")
    }

  @Test
  fun addLocalFiles_whenTheSameFileIsPickedTwice_addsItOnceAndReportsIt() =
    runTest(testDispatcher) {
      // Same bytes, so the same sha256 both times: the second pick must not burn a file slot.
      coEvery {
        attachmentManager.addPickedFile(
          any(),
          any(),
          any()
        )
      } returns Attachment(
        id = "att-1",
        name = "log.pdf",
        type = AttachmentType.ATTACHMENT_TYPE_FILE,
        sha256 = "sha-1",
      )
      val viewModel = buildViewModelForNew()

      val picked = listOf(PickedFile("uri", "log.pdf", "application/pdf", 100L))
      viewModel.addLocalFiles(picked)
      viewModel.addLocalFiles(picked)
      advanceUntilIdle()

      assertThat(viewModel.pendingAttachments.value).hasSize(1)
      assertThat((viewModel.uiState.value as TaskUiState.Success).error).isNotNull()
      // The rejected copy is already on disk with an upload scheduled — it must be reclaimed.
      coVerify(exactly = 1) { attachmentManager.delete(any()) }
    }

  // ---- attachment gate (P8.7 §9.7) ----

  @Test
  fun attachAvailable_onForeignHostedAircraft_evenWithoutOwnEntitlement() =
    runTest(testDispatcher) {
      // The host pays and the broker enforces the host's entitlement, so a member with no subscription
      // of their own can still attach on a paid owner's thing.
      every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
      every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(true)

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.attachmentUploadEnabled.value).isTrue()
    }

  @Test
  fun attachStaysOff_onOwnAircraft_whenTheEntitlementIsOff() =
    runTest(testDispatcher) {
      every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
      every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(
        false
      )

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.attachmentUploadEnabled.value).isFalse()
    }

  // ---- edit mode ----

  @Test
  fun editMode_seedsFormStateFromCard() = runTest(testDispatcher) {
    every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(
      listOf(
        MaintenanceTask(
          id = TEST_CARD_ID,
          title = "Oil change",
          reference_number = "REF-1",
        )
      )
    )
    val viewModel = buildViewModelForEdit()
    advanceUntilIdle()

    assertThat(viewModel.formState.value.title).isEqualTo("Oil change")
    assertThat(viewModel.formState.value.refNumber).isEqualTo("REF-1")
    // Baselines match the seeded values, so a freshly-loaded edit form is not "dirty".
    assertThat(viewModel.formState.value.hasChanges).isFalse()
  }

  @Test
  fun editMode_laterTaskReemission_doesNotClobberInFlightEdits() =
    runTest(testDispatcher) {
      val tasksFlow = MutableStateFlow(
        listOf(MaintenanceTask(id = TEST_CARD_ID, title = "Oil change"))
      )
      every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns tasksFlow
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      viewModel.onTitleChange("Oil change (edited)")
      // Upstream re-emits with a genuinely different record (e.g. an attachment write bumps
      // reference_number). The list must differ structurally or StateFlow would conflate it and the
      // collect — and the formSeeded guard — would never run again.
      tasksFlow.value = listOf(
        MaintenanceTask(
          id = TEST_CARD_ID,
          title = "Oil change",
          reference_number = "REF-remote",
        )
      )
      advanceUntilIdle()

      // The in-flight title edit survives, and the re-emission does not reseed other fields.
      assertThat(viewModel.formState.value.title).isEqualTo("Oil change (edited)")
      assertThat(viewModel.formState.value.refNumber).isEmpty()
    }

  // ---- resolve menu (Create Work Log / Skip This Cycle) ----

  @Test
  fun showResolveMenu_and_hideResolveMenu_toggleFormState() =
    runTest(testDispatcher) {
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      viewModel.showResolveMenu()
      assertThat(viewModel.formState.value.showResolveMenu).isTrue()

      viewModel.hideResolveMenu()
      assertThat(viewModel.formState.value.showResolveMenu).isFalse()
    }

  @Test
  fun selectCreateWorkLog_sendsNavigateToCreateLogEvent() =
    runTest(testDispatcher) {
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()
      val events = mutableListOf<TaskFormEvent>()
      val collectJob = launch { viewModel.events.collect { events.add(it) } }
      advanceUntilIdle()

      viewModel.showResolveMenu()
      viewModel.selectCreateWorkLog()
      advanceUntilIdle()

      assertThat(events).containsExactly(
        TaskFormEvent.NavigateToCreateLog(TEST_AIRCRAFT_ID, TEST_CARD_ID)
      )
      assertThat(viewModel.formState.value.showResolveMenu).isFalse()
      collectJob.cancel()
    }

  /**
   * Guards against a double-tap queueing two navigations. The screen may raise an
   * unsaved-changes prompt between the tap and this call, so the guard can't key off the menu
   * still being open.
   */
  @Test
  fun selectCreateWorkLog_sendsOnlyOneEvent_whenInvokedTwice() =
    runTest(testDispatcher) {
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()
      val events = mutableListOf<TaskFormEvent>()
      val collectJob = launch { viewModel.events.collect { events.add(it) } }
      advanceUntilIdle()

      viewModel.selectCreateWorkLog()
      viewModel.selectCreateWorkLog()
      advanceUntilIdle()

      assertThat(events).hasSize(1)
      collectJob.cancel()
    }

  @Test
  fun skipThisCycle_persistsForceCompliedStatusAtCurrentEngineHours_andInvokesOnSuccess() =
    runTest(testDispatcher) {
      coEvery {
        inspectionDataManager.updateTask(TEST_AIRCRAFT_ID, any())
      } returns Result.success(true)
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()
      val card = MaintenanceTask(id = TEST_CARD_ID, title = "Oil change")
      var succeeded = false

      viewModel.skipThisCycle(
        card = card,
        currentEngineHours = 42f,
        onSuccess = { succeeded = true },
      )
      advanceUntilIdle()

      assertThat(succeeded).isTrue()
      val persisted = slot<MaintenanceTask>()
      coVerify {
        inspectionDataManager.updateTask(
          TEST_AIRCRAFT_ID,
          capture(persisted)
        )
      }
      val status = persisted.captured.force_complied_status
      assertThat(status).isNotNull()
      assertThat(status!!.complied_engine_hours).isEqualTo(42f)
      assertThat(status.complied_date).isNotNull()
    }

  /**
   * TaskDueManager resolves force-due overrides and returns before it reads force-complied
   * state, so a skip that left an override in place would never move the next due — the user
   * would see a "cycle skipped" toast and an unchanged due date.
   */
  @Test
  fun skipThisCycle_clearsRescheduleOverride() = runTest(testDispatcher) {
    coEvery {
      inspectionDataManager.updateTask(TEST_AIRCRAFT_ID, any())
    } returns Result.success(true)
    val viewModel = buildViewModelForEdit()
    advanceUntilIdle()
    val rescheduled = MaintenanceTask(
      id = TEST_CARD_ID,
      title = "Oil change",
      force_due_date = toWireInstant(1_800_000_000L),
      force_due_engine_hour = 1500f,
    )

    viewModel.skipThisCycle(
      card = rescheduled,
      currentEngineHours = 42f,
      onSuccess = {},
    )
    advanceUntilIdle()

    val persisted = slot<MaintenanceTask>()
    coVerify {
      inspectionDataManager.updateTask(
        TEST_AIRCRAFT_ID,
        capture(persisted)
      )
    }
    assertThat(persisted.captured.force_due_date).isNull()
    assertThat(persisted.captured.force_due_engine_hour).isEqualTo(0f)
    assertThat(persisted.captured.force_complied_status).isNotNull()
  }

  // ---- preview banner due readings (#347) ----

  /**
   * The adjustments banner's "Current" reading has to agree with the dashboard task cards, so
   * it reads the effective due — the stored card, skip and override included — while the
   * rules-only natural due stays available for the reschedule "Was …" line.
   */
  @Test
  fun loadData_exposesBothTheEffectiveAndTheRulesOnlyDue() =
    runTest(testDispatcher) {
      val skipped = MaintenanceTask(
        id = TEST_CARD_ID,
        title = "Oil change",
        force_complied_status = ForceCompliedStatus(complied_engine_hours = 10f),
      )
      every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(skipped)
      )
      every { taskDueManager.computeNextDue(any(), any(), any()) } answers {
        // Stand in for the force-complied advancement: the stripped card still reads 9/30, the
        // stored one has been advanced a cycle to 11/30.
        if (firstArg<MaintenanceTask>().force_complied_status != null) {
          DueMetadata(nextDueDate = LocalDate(2026, 11, 30))
        } else {
          DueMetadata(nextDueDate = LocalDate(2026, 9, 30))
        }
      }

      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      val state = viewModel.uiState.value as TaskUiState.Success
      assertThat(state.effectiveDueMetadata?.nextDueDate).isEqualTo(
        LocalDate(
          2026,
          11,
          30
        )
      )
      assertThat(state.naturalDueMetadata?.nextDueDate).isEqualTo(
        LocalDate(
          2026,
          9,
          30
        )
      )
    }

  // ---- force-complied status vs. schedule edits ----

  /**
   * A skip marks one specific cycle complete. Once the schedule moves, that cycle no longer
   * describes anything real, so saving the edited schedule must drop it rather than silently
   * advance a due date the user never skipped.
   */
  @Test
  fun saveEditedTask_dropsForceCompliedStatus_whenScheduleChanged() =
    runTest(testDispatcher) {
      val stored = skippedCard(forceDueEngine = 0f)
      every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(stored)
      )
      coEvery {
        inspectionDataManager.updateTask(TEST_AIRCRAFT_ID, any())
      } returns Result.success(true)
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      viewModel.saveEditedTaskFrom(stored.copy(force_due_engine_hour = 1500f))
      advanceUntilIdle()

      val persisted = slot<MaintenanceTask>()
      coVerify {
        inspectionDataManager.updateTask(
          TEST_AIRCRAFT_ID,
          capture(persisted)
        )
      }
      assertThat(persisted.captured.force_complied_status).isNull()
    }

  @Test
  fun saveEditedTask_keepsForceCompliedStatus_whenScheduleUnchanged() =
    runTest(testDispatcher) {
      val stored = skippedCard(forceDueEngine = 0f)
      every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(
        listOf(stored)
      )
      coEvery {
        inspectionDataManager.updateTask(TEST_AIRCRAFT_ID, any())
      } returns Result.success(true)
      val viewModel = buildViewModelForEdit()
      advanceUntilIdle()

      // Editing a non-schedule field must leave the recorded skip alone.
      viewModel.saveEditedTaskFrom(stored.copy(title = "Oil change (50h)"))
      advanceUntilIdle()

      val persisted = slot<MaintenanceTask>()
      coVerify {
        inspectionDataManager.updateTask(
          TEST_AIRCRAFT_ID,
          capture(persisted)
        )
      }
      assertThat(persisted.captured.force_complied_status).isNotNull()
    }

  // ---- helpers ----

  private fun skippedCard(forceDueEngine: Float) = MaintenanceTask(
    id = TEST_CARD_ID,
    title = "Oil change",
    force_due_engine_hour = forceDueEngine,
    force_complied_status = ForceCompliedStatus(complied_engine_hours = 10f),
  )

  /** Calls [TaskViewModel.saveEditedTask] with the field values carried by [card]. */
  private fun TaskViewModel.saveEditedTaskFrom(card: MaintenanceTask) =
    saveEditedTask(
      cardId = card.id,
      title = card.title,
      type = card.type,
      component = card.component,
      rules = card.rules,
      referenceNumber = card.reference_number,
      complianceAuthority = card.compliance_authority,
      complianceDetails = card.compliance_details,
      isOneTime = card.is_one_time,
      forceDueDate = card.force_due_date,
      forceDueEngine = card.force_due_engine_hour,
      forceCompliedStatus = card.force_complied_status,
      notes = card.notes,
      onSuccess = {},
    )

  private fun buildViewModelForNew(): TaskViewModel =
    TaskViewModel(
      inspectionDataManager = inspectionDataManager,
      attachmentManager = attachmentManager,
      auth = auth,
      maintenanceLogManager = maintenanceLogManager,
      subscriptionManager = subscriptionManager,
      sharingManager = sharingManager,
      taskDueManager = taskDueManager,
      savedStateHandle = SavedStateHandle(mapOf(Screen.AIRCRAFT_ID to TEST_AIRCRAFT_ID)),
    )

  private fun buildViewModelForEdit(): TaskViewModel =
    TaskViewModel(
      inspectionDataManager = inspectionDataManager,
      attachmentManager = attachmentManager,
      auth = auth,
      maintenanceLogManager = maintenanceLogManager,
      subscriptionManager = subscriptionManager,
      sharingManager = sharingManager,
      taskDueManager = taskDueManager,
      savedStateHandle = SavedStateHandle(
        mapOf(
          Screen.AIRCRAFT_ID to TEST_AIRCRAFT_ID,
          Screen.CARD_ID to TEST_CARD_ID,
        )
      ),
    )
}
