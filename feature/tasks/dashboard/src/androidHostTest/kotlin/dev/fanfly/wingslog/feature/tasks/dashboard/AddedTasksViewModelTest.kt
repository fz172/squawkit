package dev.fanfly.wingslog.feature.tasks.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddedTasksViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val recentlyAdded = RecentlyAddedTasks()
  private val tasks = mockk<TaskDataManager>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    coEvery { tasks.deleteTask(any(), any()) } returns Result.success(true)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  @Test
  fun aBatchIsAnnouncedOnceAndItsTasksAreMarkedNew() = runTest(dispatcher) {
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)
    backgroundScope.launch { vm.newIds.collect {} }
    backgroundScope.launch { vm.toAnnounce.collect {} }

    recentlyAdded.record(THING, listOf("t1", "t2"))
    advanceUntilIdle()
    assertThat(vm.toAnnounce.value).isEqualTo(2)
    assertThat(vm.newIds.value).containsExactly("t1", "t2")

    vm.onAnnounced()
    advanceUntilIdle()
    assertThat(vm.toAnnounce.value).isNull()
    // Still NEW after the snackbar has gone.
    assertThat(vm.newIds.value).containsExactly("t1", "t2")
  }

  @Test
  fun undoDeletesTheBatchAndClearsTheBadges() = runTest(dispatcher) {
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)
    backgroundScope.launch { vm.newIds.collect {} }
    recentlyAdded.record(THING, listOf("t1", "t2"))
    advanceUntilIdle()

    vm.onUndo()
    advanceUntilIdle()

    coVerify { tasks.deleteTask(THING, "t1") }
    coVerify { tasks.deleteTask(THING, "t2") }
    assertThat(vm.newIds.value).isEmpty()
  }

  @Test
  fun anotherThingsBatchIsNotThisTabs() = runTest(dispatcher) {
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)
    backgroundScope.launch { vm.toAnnounce.collect {} }
    recentlyAdded.record("other", listOf("t9"))
    advanceUntilIdle()

    assertThat(vm.toAnnounce.value).isNull()
    vm.onUndo()
    advanceUntilIdle()
    coVerify(exactly = 0) { tasks.deleteTask(any(), any()) }
  }

  private companion object {
    const val THING = "thing-1"
  }
}
