package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_deleted
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes

private const val THING_ID = "thing-1"

/**
 * The dashboard's quick actions (design §5.1), and the §8 guard that each one goes through the
 * manager — the manager's tombstone is what fans the collaborator notification out, so a delete
 * that reached the store directly would be silent for everyone else on a shared Thing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThingOverviewViewModelTest {

  private val fleetManager: FleetManager = mockk()
  private val logManager: MaintenanceLogManager = mockk()
  private val taskDataManager: TaskDataManager = mockk()
  private val taskStatusManager: TaskStatusManager = mockk()
  private val attachmentOpener: AttachmentOpener = mockk()
  private val attachmentManager: AttachmentManager = mockk()
  private val squawkManager: SquawkManager = mockk()
  private val sharingManager: SharingManager = mockk()
  private val thingScopeResolver: ThingScopeResolver = mockk()
  private val analytics = RecordingAnalyticsManager()
  private val auth: FirebaseAuth = mockk()

  private val thing = Thing(id = THING_ID, template = AirplaneTemplate.TEMPLATE)
  private val squawk = Squawk(id = "s1", title = "Transponder intermittent")
  private val oilChange = MaintenanceTask(
    id = "c1",
    title = "Oil change",
    component = ComponentType.COMPONENT_ENGINE,
  )
  private val dueOilChange = MaintenanceTaskWithStatus(
    card = oilChange,
    dueStatus = DueMetadata(status = DueStatus.DUE_SOON),
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { fleetManager.loadThing(THING_ID) } returns flowOf(thing)
    every { logManager.observeLogs(THING_ID) } returns flowOf(emptyList())
    every { logManager.observeMaintenanceOverview(THING_ID) } returns flowOf(null)
    every { taskStatusManager.observeTasksWithStatus(THING_ID) } returns
      flowOf(listOf(dueOilChange))
    every { squawkManager.observeSquawks(THING_ID) } returns flowOf(listOf(squawk))
    every { thingScopeResolver.resolve(THING_ID) } returns flowOf(null)
    every { attachmentOpener.downloadingIds } returns MutableStateFlow(emptySet())
    every { sharingManager.observeMyRole(THING_ID) } returns flowOf(ShareRole.OWNER)
    every { sharingManager.observeIsShared(THING_ID) } returns flowOf(false)
    every { auth.currentUser } returns null
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = ThingOverviewViewModel(
    fleetManager, logManager, taskDataManager, taskStatusManager, attachmentOpener,
    attachmentManager, squawkManager, sharingManager, thingScopeResolver,
    BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE), analytics, auth, THING_ID,
  )

  private val ThingOverviewViewModel.success: ThingOverviewUiState.Success
    get() = uiState.value as ThingOverviewUiState.Success

  @Test
  fun confirmDeleteSquawk_deletesThroughTheManager_clearsTheIdsAndSaysSo() = runTest {
    coEvery { squawkManager.deleteSquawk(THING_ID, "s1") } returns Result.success(true)
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.ShowSquawkDetail(vm.success.squawks.first()))
    vm.onAction(ThingOverviewAction.DeleteSquawkClick(vm.success.squawks.first()))
    assertThat(vm.success.deletingSquawkId).isEqualTo("s1")

    vm.onAction(ThingOverviewAction.ConfirmDeleteSquawk)

    coVerify(exactly = 1) { squawkManager.deleteSquawk(THING_ID, "s1") }
    assertThat(vm.success.deletingSquawkId).isNull()
    assertThat(vm.success.selectedSquawk).isNull()
    assertThat(vm.events.first())
      .isEqualTo(
        ThingOverviewEvent.ShowMessage(
          UiText.StringRes(SquawkRes.string.squawk_deleted, listOf("Squawk"))
        )
      )
  }

  @Test
  fun deleteSquawkFailure_keepsTheCardAndReportsIt() = runTest {
    coEvery { squawkManager.deleteSquawk(THING_ID, "s1") } returns
      Result.failure(IllegalStateException("offline"))
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.DeleteSquawkClick(vm.success.squawks.first()))

    vm.onAction(ThingOverviewAction.ConfirmDeleteSquawk)

    // The record is still there to try again on, so only the dialog closes.
    assertThat(vm.success.squawks.map { it.squawk.id }).containsExactly("s1")
    assertThat(vm.success.deletingSquawkId).isNull()
    assertThat(vm.events.first())
      .isEqualTo(ThingOverviewEvent.ShowMessage(UiText.StringRes(CoreRes.string.delete_failed)))
  }

  @Test
  fun confirmDismissSquawk_passesTheChosenReason() = runTest {
    coEvery {
      squawkManager.dismissSquawk(THING_ID, "s1", SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE)
    } returns Result.success(Unit)
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.SquawkDismissClick("s1"))

    vm.onAction(
      ThingOverviewAction.ConfirmDismissSquawk(
        SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE
      )
    )

    coVerify(exactly = 1) {
      squawkManager.dismissSquawk(THING_ID, "s1", SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE)
    }
    assertThat(vm.success.dismissingSquawkId).isNull()
  }

  @Test
  fun cancelledConfirmations_writeNothingAndLogNothing() = runTest {
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.DeleteSquawkClick(vm.success.squawks.first()))
    vm.onAction(ThingOverviewAction.CancelDeleteSquawk)
    vm.onAction(ThingOverviewAction.TaskSkipClick(dueOilChange))
    vm.onAction(ThingOverviewAction.CancelSkipTask)

    coVerify(exactly = 0) { squawkManager.deleteSquawk(any(), any()) }
    coVerify(exactly = 0) { taskDataManager.skipCycle(any(), any(), any()) }
    assertThat(analytics.countOf("record_quick_action")).isEqualTo(0)
  }

  @Test
  fun confirmSkipTask_skipsAgainstTheDashboardsCurrentReading() = runTest {
    // The dashboard already holds the reading TaskViewModel derives; the write must be the same.
    every { logManager.observeLogs(THING_ID) } returns flowOf(emptyList())
    coEvery { taskDataManager.skipCycle(THING_ID, oilChange, any()) } returns Result.success(true)
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.TaskSkipClick(dueOilChange))
    assertThat(vm.success.skippingTaskId).isEqualTo("c1")

    vm.onAction(ThingOverviewAction.ConfirmSkipTask)

    coVerify(exactly = 1) { taskDataManager.skipCycle(THING_ID, oilChange, 0f) }
    assertThat(vm.success.skippingTaskId).isNull()
    assertThat(analytics.paramsFor("record_quick_action").single())
      .containsAtLeast("surface", "tasks", "action", "skip")
  }

  @Test
  fun confirmDeleteTask_deletesThroughTheManager() = runTest {
    coEvery { taskDataManager.deleteTask(THING_ID, "c1") } returns Result.success(true)
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.DeleteTaskClick(dueOilChange))
    assertThat(vm.success.deletingTaskId).isEqualTo("c1")

    vm.onAction(ThingOverviewAction.ConfirmDeleteTask)

    coVerify(exactly = 1) { taskDataManager.deleteTask(THING_ID, "c1") }
    assertThat(vm.success.deletingTaskId).isNull()
  }

  @Test
  fun choosingABubbleOption_logsTheCommitAndClosesTheBubble() = runTest {
    val vm = viewModel()
    vm.onAction(ThingOverviewAction.SquawkResolveClick(vm.success.squawks.first()))
    assertThat(vm.success.resolvingSquawkId).isEqualTo("s1")

    vm.onAction(ThingOverviewAction.SquawkFixedClick("s1"))

    assertThat(vm.success.resolvingSquawkId).isNull()
    assertThat(analytics.paramsFor("record_quick_action").single())
      .containsAtLeast("surface", "squawks", "action", "resolve", "source", "swipe")
  }
}
