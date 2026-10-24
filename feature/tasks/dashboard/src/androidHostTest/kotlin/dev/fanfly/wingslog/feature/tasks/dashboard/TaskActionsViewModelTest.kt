package dev.fanfly.wingslog.feature.tasks.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.ComponentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val THING_ID = "thing-1"

/**
 * The tasks dashboard's quick actions (design §5.1), and the §8 guard that each one goes through
 * the manager — the manager's tombstone is what fans the collaborator notification out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskActionsViewModelTest {

  private val taskDataManager: TaskDataManager = mockk()
  private val taskStatusManager: TaskStatusManager = mockk()
  private val logManager: MaintenanceLogManager = mockk()
  private val commentManager: CommentManager = mockk(relaxed = true) {
    every { observeComments(any()) } returns flowOf(emptyList())
  }
  private val analytics = RecordingAnalyticsManager()

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
    every { taskStatusManager.observeTasksWithStatus(THING_ID) } returns
      flowOf(listOf(dueOilChange))
    every { logManager.observeLogs(THING_ID) } returns flowOf(emptyList())
    every { logManager.observeCurrentReadings(THING_ID) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = TaskActionsViewModel(
    taskDataManager = taskDataManager,
    taskStatusManager = taskStatusManager,
    logManager = logManager,
    commentManager = commentManager,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE),
    analytics = analytics,
    thingId = THING_ID,
    templateId = AirplaneTemplate.ID,
  )

  @Test
  fun cancelledConfirmations_writeNothingAndLogNothing() = runTest {
    val vm = viewModel()
    vm.onSkipClick(dueOilChange)
    vm.cancelSkip()

    coVerify(exactly = 0) { taskDataManager.skipCycle(any(), any(), any()) }
    assertThat(analytics.countOf("record_quick_action")).isEqualTo(0)
  }

  @Test
  fun confirmSkipTask_skipsAgainstTheDashboardsCurrentReading() = runTest {
    coEvery {
      taskDataManager.skipCycle(
        THING_ID,
        oilChange,
        any(),
      )
    } returns Result.success(true)
    val vm = viewModel()
    vm.onSkipClick(dueOilChange)
    assertThat(vm.uiState.value.skippingTaskId).isEqualTo("c1")

    vm.confirmSkip()

    coVerify(exactly = 1) { taskDataManager.skipCycle(THING_ID, oilChange, 0f) }
    assertThat(vm.uiState.value.skippingTaskId).isNull()
    assertThat(
      analytics.paramsFor("record_quick_action")
        .single()
    )
      .containsAtLeast("surface", "tasks", "action", "skip")
  }

  @Test
  fun confirmDeleteTask_deletesThroughTheManager() = runTest {
    coEvery {
      taskDataManager.deleteTask(
        THING_ID,
        "c1",
      )
    } returns Result.success(true)
    val vm = viewModel()
    vm.onDeleteClick(dueOilChange)
    assertThat(vm.uiState.value.deletingTaskId).isEqualTo("c1")

    vm.confirmDelete()

    coVerify(exactly = 1) { taskDataManager.deleteTask(THING_ID, "c1") }
    assertThat(vm.uiState.value.deletingTaskId).isNull()
  }
}
