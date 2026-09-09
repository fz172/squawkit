package dev.fanfly.wingslog.feature.logs.viewing.log.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import dev.fanfly.wingslog.thing.MaintenanceLog
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.logs.sharedassets.generated.resources.log_deleted
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes

private const val THING_ID = "thing-1"

/**
 * Deleting a log from its card's swipe panel. The §8 guard is the `deleteLog` verification: the
 * manager's tombstone is what fans the collaborator notification out, so a delete that reached the
 * store directly would be silent for everyone else on a shared Thing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceLogListDeleteTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val logManager: MaintenanceLogManager = mockk(relaxed = true)
  private val tasks: TaskDataManager = mockk(relaxed = true)
  private val sharing: SharingManager = mockk(relaxed = true)
  private val technicians: TechnicianManager = mockk(relaxed = true)
  private val squawks: SquawkManager = mockk(relaxed = true)
  private val auth: FirebaseAuth = mockk(relaxed = true)
  private val analytics = RecordingAnalyticsManager()
  private val fixedClock = object : Clock {
    override fun now() = Instant.parse("2026-09-06T12:00:00Z")
  }

  private val oil = MaintenanceLog(
    id = "oil",
    timestamp = toWireInstant(Instant.parse("2026-09-01T12:00:00Z").epochSeconds),
    work_description = "Oil and filter change",
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { logManager.observeLogs(THING_ID) } returns flowOf(listOf(oil))
    every { logManager.observeLogAuthors(THING_ID) } returns flowOf(emptyMap())
    every { tasks.observeTasks(THING_ID) } returns flowOf(emptyList())
    every { sharing.observeLinkedTechnicians(THING_ID) } returns flowOf(emptyList())
    every { sharing.observeIsShared(THING_ID) } returns flowOf(false)
    every { technicians.observeSelf() } returns flowOf(null)
    every { squawks.observeSquawks(THING_ID) } returns flowOf(emptyList())
    every { auth.currentUser } returns null
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = MaintenanceLogListViewModel(
    logManager = logManager,
    inspectionDataManager = tasks,
    sharingManager = sharing,
    technicianManager = technicians,
    squawkManager = squawks,
    auth = auth,
    searchEngine = SearchEngineImpl(),
    tuning = SearchTuning(queryDebounceMillis = 0, dispatcher = dispatcher),
    analytics = analytics,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
    thingId = THING_ID,
    templateId = "airplane",
    clock = fixedClock,
    timeZone = TimeZone.UTC,
  )

  private fun MaintenanceLogListViewModel.success() =
    uiState.value as MaintenanceLogListUiState.Success

  @Test
  fun confirmDeleteLog_deletesThroughTheManagerAndSaysSo() = runTest {
    coEvery { logManager.deleteLog(THING_ID, "oil") } returns Result.success(true)
    val vm = viewModel()
    vm.onDeleteLogClick(oil)
    assertThat(vm.success().deletingLog).isEqualTo(oil)

    vm.confirmDeleteLog()

    coVerify(exactly = 1) { logManager.deleteLog(THING_ID, "oil") }
    assertThat(vm.success().deletingLog).isNull()
    // The lexicon's noun, never a hard-coded "log" (PRD R24).
    assertThat(vm.events.first()).isEqualTo(
      MaintenanceLogListEvent.ShowMessage(
        UiText.StringRes(LogsRes.string.log_deleted, listOf("Work log"))
      )
    )
    assertThat(analytics.paramsFor("record_quick_action").single())
      .containsAtLeast("surface", "logs", "action", "delete", "source", "swipe")
  }

  @Test
  fun deleteFailure_keepsTheCardAndReportsIt() = runTest {
    coEvery { logManager.deleteLog(THING_ID, "oil") } returns
      Result.failure(IllegalStateException("offline"))
    val vm = viewModel()
    vm.onDeleteLogClick(oil)

    vm.confirmDeleteLog()

    assertThat(vm.success().logs.map { it.id }).containsExactly("oil")
    assertThat(vm.success().deletingLog).isNull()
    assertThat(vm.events.first()).isEqualTo(
      MaintenanceLogListEvent.ShowMessage(UiText.StringRes(CoreRes.string.delete_failed))
    )
    assertThat(analytics.countOf("record_quick_action")).isEqualTo(0)
  }

  @Test
  fun cancelDeleteLog_writesNothing() = runTest {
    val vm = viewModel()
    vm.onDeleteLogClick(oil)

    vm.cancelDeleteLog()

    assertThat(vm.success().deletingLog).isNull()
    coVerify(exactly = 0) { logManager.deleteLog(any(), any()) }
  }
}
