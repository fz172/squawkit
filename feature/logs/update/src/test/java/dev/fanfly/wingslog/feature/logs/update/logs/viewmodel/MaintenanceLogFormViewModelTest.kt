package dev.fanfly.wingslog.feature.logs.update.logs.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceLogFormViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var logManager: MaintenanceLogManager
  private lateinit var fleetManager: FleetManager
  private lateinit var inspectionDataManager: TaskDataManager
  private lateinit var squawkManager: SquawkManager
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var technicianManager: TechnicianManager
  private lateinit var auth: FirebaseAuth
  private lateinit var featureLabManager: FeatureLabManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    logManager = mockk(relaxed = true)
    fleetManager = mockk(relaxed = true)
    inspectionDataManager = mockk(relaxed = true)
    squawkManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    technicianManager = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    featureLabManager = mockk(relaxed = true)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.isAnonymous } returns false
    every { auth.currentUser } returns mockUser

    // Prevent the init-block flows from suspending forever.
    every { featureLabManager.observe() } returns flowOf(FeatureFlags())
    every { fleetManager.loadAircraft(TEST_AIRCRAFT_ID) } returns flowOf(null)
    every { inspectionDataManager.observeTasks(TEST_AIRCRAFT_ID) } returns flowOf(emptyList())
    every { technicianManager.observeTechnicians() } returns flowOf(emptyList())
    every { technicianManager.observeSelfId() } returns flowOf(null)
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

  // ---- helpers ----

  private fun buildViewModelForEdit(): MaintenanceLogFormViewModel =
    MaintenanceLogFormViewModel(
      logManager = logManager,
      fleetManager = fleetManager,
      inspectionDataManager = inspectionDataManager,
      squawkManager = squawkManager,
      attachmentManager = attachmentManager,
      technicianManager = technicianManager,
      auth = auth,
      featureLabManager = featureLabManager,
      savedStateHandle = SavedStateHandle(
        mapOf(
          Screen.AIRCRAFT_ID to TEST_AIRCRAFT_ID,
          Screen.LOG_ID to TEST_LOG_ID,
        )
      ),
    )

  private fun buildViewModelForNew(): MaintenanceLogFormViewModel =
    MaintenanceLogFormViewModel(
      logManager = logManager,
      fleetManager = fleetManager,
      inspectionDataManager = inspectionDataManager,
      squawkManager = squawkManager,
      attachmentManager = attachmentManager,
      technicianManager = technicianManager,
      auth = auth,
      featureLabManager = featureLabManager,
      savedStateHandle = SavedStateHandle(
        mapOf(Screen.AIRCRAFT_ID to TEST_AIRCRAFT_ID)
      ),
    )
}
