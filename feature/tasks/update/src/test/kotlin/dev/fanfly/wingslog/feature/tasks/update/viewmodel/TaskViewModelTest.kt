package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
  private lateinit var featureLabManager: FeatureLabManager
  private lateinit var sharingManager: SharingManager
  private lateinit var taskDueManager: TaskDueManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    inspectionDataManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    maintenanceLogManager = mockk(relaxed = true)
    featureLabManager = mockk(relaxed = true)
    sharingManager = mockk(relaxed = true)
    taskDueManager = mockk(relaxed = true)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.isAnonymous } returns false
    every { auth.currentUser } returns mockUser

    // Prevent the load flows from suspending forever.
    every { featureLabManager.observe() } returns flowOf(FeatureFlags())
    every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(emptyList())
    every { maintenanceLogManager.observeLogs(TEST_AIRCRAFT_ID) } returns flowOf(emptyList())
    every { maintenanceLogManager.observeMaintenanceOverview(TEST_AIRCRAFT_ID) } returns flowOf(null)
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
  fun onTitleChange_updatesFormStateAndMarksChanged() = runTest(testDispatcher) {
    val viewModel = buildViewModelForNew()

    viewModel.onTitleChange("Annual inspection")

    assertThat(viewModel.formState.value.title).isEqualTo("Annual inspection")
    assertThat(viewModel.formState.value.hasChanges).isTrue()
  }

  /** The #254 regression: picking a file must not wipe fields typed before it. */
  @Test
  fun addLocalFiles_doesNotClobberInProgressFormFields() = runTest(testDispatcher) {
    coEvery { attachmentManager.addPickedFile(any(), any(), any()) } returns mockk(relaxed = true)
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
  fun editMode_laterTaskReemission_doesNotClobberInFlightEdits() = runTest(testDispatcher) {
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

  // ---- helpers ----

  private fun buildViewModelForNew(): TaskViewModel =
    TaskViewModel(
      inspectionDataManager = inspectionDataManager,
      attachmentManager = attachmentManager,
      auth = auth,
      maintenanceLogManager = maintenanceLogManager,
      featureLabManager = featureLabManager,
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
      featureLabManager = featureLabManager,
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
