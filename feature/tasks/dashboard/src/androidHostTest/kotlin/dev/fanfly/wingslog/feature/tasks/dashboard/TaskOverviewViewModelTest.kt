package dev.fanfly.wingslog.feature.tasks.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.task.MaintenanceTask
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

@OptIn(ExperimentalCoroutinesApi::class)
class TaskOverviewViewModelTest {

  private val taskStatusManager: TaskStatusManager = mockk()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  @Test
  fun partitionsOverdueAndNextDueFromActiveTasks() = runTest {
    val overdue = MaintenanceTaskWithStatus(
      card = MaintenanceTask(id = "t-overdue", title = "Annual"),
      dueStatus = DueMetadata(status = DueStatus.OVERDUE),
    )
    val dueSoon = MaintenanceTaskWithStatus(
      card = MaintenanceTask(id = "t-soon", title = "Oil change"),
      dueStatus = DueMetadata(status = DueStatus.DUE_SOON),
    )
    val upcoming = MaintenanceTaskWithStatus(
      card = MaintenanceTask(id = "t-upcoming", title = "ELT battery"),
      dueStatus = DueMetadata(status = DueStatus.NORMAL),
    )
    val complied = MaintenanceTaskWithStatus(
      card = MaintenanceTask(id = "t-complied", title = "AD 2024-01"),
      dueStatus = DueMetadata(status = DueStatus.COMPLIED),
    )
    every { taskStatusManager.observeTasksWithStatus(THING_ID) } returns flowOf(
      listOf(overdue, dueSoon, upcoming, complied)
    )

    val state = TaskOverviewViewModel(taskStatusManager, THING_ID).uiState.value

    assertThat(state.loaded).isTrue()
    assertThat(state.overdueTasks).containsExactly(overdue, dueSoon).inOrder()
    assertThat(state.nextDueTask).isEqualTo(upcoming)
  }
}
