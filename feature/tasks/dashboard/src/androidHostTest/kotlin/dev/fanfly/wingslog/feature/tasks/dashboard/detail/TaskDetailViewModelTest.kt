package dev.fanfly.wingslog.feature.tasks.dashboard.detail

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.TemplateId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksRes
import wingslog.feature.tasks.sharedassets.generated.resources.task_deleted

private val THING = ThingId("thing-1")
private const val THING_ID = "thing-1"
private val TASK_ID = MaintenanceTaskId("c1")

/**
 * The tasks dashboard's quick actions (design §5.1), and the §8 guard that each one goes through
 * the manager — the manager's tombstone is what fans the collaborator notification out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

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
    every { taskStatusManager.observeTasksWithStatus(THING) } returns
      flowOf(listOf(dueOilChange))
    every { logManager.observeLogs(THING_ID) } returns flowOf(emptyList())
    every { logManager.observeCurrentReadings(THING_ID) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = TaskDetailViewModel(
    taskDataManager = taskDataManager,
    taskStatusManager = taskStatusManager,
    logManager = logManager,
    commentManager = commentManager,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE),
    analytics = analytics,
    thingId = THING,
    templateId = TemplateId(AirplaneTemplate.ID),
  )

  @Test
  fun cancelledConfirmations_writeNothingAndLogNothing() = runTest {
    val vm = viewModel()
    vm.onSkipClick(dueOilChange)
    vm.cancelSkip()

    coVerify(exactly = 0) { taskDataManager.skipCycle(any<ThingId>(), any(), any()) }
    assertThat(analytics.countOf("record_quick_action")).isEqualTo(0)
  }

  @Test
  fun confirmSkipTask_skipsAgainstTheDashboardsCurrentReading() = runTest {
    coEvery {
      taskDataManager.skipCycle(
        THING,
        oilChange,
        any(),
      )
    } returns Result.success(true)
    val vm = viewModel()
    vm.onSkipClick(dueOilChange)
    assertThat(vm.uiState.value.skippingTaskId).isEqualTo(TASK_ID)

    vm.confirmSkip()

    coVerify(exactly = 1) { taskDataManager.skipCycle(THING, oilChange, 0f) }
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
        THING,
        TASK_ID,
      )
    } returns Result.success(true)
    val vm = viewModel()
    vm.onDeleteClick(dueOilChange)
    assertThat(vm.uiState.value.deletingTaskId).isEqualTo(TASK_ID)

    vm.confirmDelete()

    coVerify(exactly = 1) { taskDataManager.deleteTask(THING, TASK_ID) }
    assertThat(vm.uiState.value.deletingTaskId).isNull()
    assertThat(vm.events.first())
      .isEqualTo(UiText.StringRes(TasksRes.string.task_deleted, listOf("Maintenance task")))
  }

  @Test
  fun deleteTaskFailure_closesDialogAndReportsIt() = runTest {
    coEvery {
      taskDataManager.deleteTask(
        THING,
        TASK_ID,
      )
    } returns Result.failure(IllegalStateException("offline"))
    val vm = viewModel()
    vm.onDeleteClick(dueOilChange)

    vm.confirmDelete()

    assertThat(vm.uiState.value.deletingTaskId).isNull()
    assertThat(vm.events.first())
      .isEqualTo(UiText.StringRes(CoreRes.string.delete_failed))
  }

  @Test
  fun openingATaskSheet_opensThatTasksThread_andClosingItClosesTheThread() = runTest {
    val vm = viewModel()
    assertThat(vm.commentThread.value).isNull()

    vm.showDetail(dueOilChange)
    advanceUntilIdle()

    assertThat(vm.commentThread.value).isNotNull()
    verify {
      commentManager.observeComments(
        CommentTarget.task(
          THING,
          TASK_ID,
        )
      )
    }

    vm.dismissDetail()
    advanceUntilIdle()

    assertThat(vm.commentThread.value).isNull()
  }

  @Test
  fun showDetail_populatesLogsForSelectedTask() = runTest {
    val matchingLog = MaintenanceLog(
      id = "l1",
      inspection_ids = listOf("c1"),
      work_description = "Changed oil",
    )
    val otherLog = MaintenanceLog(
      id = "l2",
      inspection_ids = listOf("c2"),
      work_description = "Other work",
    )
    every { logManager.observeLogs(THING_ID) } returns flowOf(listOf(matchingLog, otherLog))

    val vm = viewModel()
    advanceUntilIdle()

    vm.showDetail(dueOilChange)

    assertThat(vm.uiState.value.selectedTask).isEqualTo(dueOilChange)
    assertThat(vm.uiState.value.logsForSelectedTask).containsExactly(matchingLog)

    vm.dismissDetail()
    assertThat(vm.uiState.value.selectedTask).isNull()
    assertThat(vm.uiState.value.logsForSelectedTask).isEmpty()
  }
}
