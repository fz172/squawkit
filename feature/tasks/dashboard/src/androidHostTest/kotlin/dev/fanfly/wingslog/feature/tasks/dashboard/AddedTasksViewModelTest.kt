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
import kotlinx.coroutines.test.runCurrent
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
  fun aBatchIsHandedOutOnce() = runTest(dispatcher) {
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)
    val said = mutableListOf<List<String>>()
    backgroundScope.launch { vm.added.collect { said += it } }

    recentlyAdded.record(THING, listOf("t1", "t2"))
    runCurrent()

    assertThat(said).containsExactly(listOf("t1", "t2"))
    // Taken, so a tab opened later does not say it again.
    assertThat(recentlyAdded.batch.value).isNull()
    val later = mutableListOf<List<String>>()
    backgroundScope.launch { vm.added.collect { later += it } }
    runCurrent()
    assertThat(later).isEmpty()
  }

  @Test
  fun aBatchAddedBeforeTheTabOpensIsStillSaid() = runTest(dispatcher) {
    recentlyAdded.record(THING, listOf("t1"))
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)
    val said = mutableListOf<List<String>>()
    backgroundScope.launch { vm.added.collect { said += it } }
    runCurrent()

    assertThat(said).containsExactly(listOf("t1"))
  }

  @Test
  fun undoDeletesTheBatch() = runTest(dispatcher) {
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)

    vm.onUndo(listOf("t1", "t2"))
    advanceUntilIdle()

    coVerify { tasks.deleteTask(THING, "t1") }
    coVerify { tasks.deleteTask(THING, "t2") }
  }

  @Test
  fun anotherThingsBatchIsNotThisTabs() = runTest(dispatcher) {
    val vm = AddedTasksViewModel(recentlyAdded, tasks, THING)
    val said = mutableListOf<List<String>>()
    backgroundScope.launch { vm.added.collect { said += it } }
    recentlyAdded.record("other", listOf("t9"))
    runCurrent()

    assertThat(said).isEmpty()
    // Left for its own tab.
    assertThat(recentlyAdded.batch.value?.thingId).isEqualTo("other")
  }

  private companion object {
    const val THING = "thing-1"
  }
}
