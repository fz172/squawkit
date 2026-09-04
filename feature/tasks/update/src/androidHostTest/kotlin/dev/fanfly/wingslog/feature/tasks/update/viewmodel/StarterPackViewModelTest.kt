package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.update.starter.StarterPackViewModel
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.StarterTask
import dev.fanfly.wingslog.thing.Thing
import dev.fanfly.wingslog.thing.ThingTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The two §13 events and what they count. `starter_tasks_offered` is the denominator: without it
 * a low acceptance count cannot be told apart from packs never shown, so it has to fire exactly
 * when a pack is on screen — and not for a Thing with nothing to offer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StarterPackViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val analytics = RecordingAnalyticsManager()
  private val fleetManager = mockk<FleetManager>()
  private val taskDataManager = mockk<TaskDataManager>()
  private val written = mutableListOf<MaintenanceTask>()

  private val pack = listOf(
    StarterTask(title = "HVAC filter", description = "Quarterly", interval_months = 3, default_selected = true),
    StarterTask(title = "Clean gutters", description = "Twice a year", interval_months = 6, default_selected = true),
    StarterTask(title = "Septic pump-out", description = "If on septic", interval_months = 36),
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    val card = slot<MaintenanceTask>()
    coEvery { taskDataManager.addTask(THING_ID, capture(card)) } answers {
      written += card.captured
      Result.success(true)
    }
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(starterTasks: List<StarterTask>): StarterPackViewModel {
    val thing = Thing(
      id = THING_ID,
      template = ThingTemplate(id = "home", version = 7, starter_tasks = starterTasks),
    )
    every { fleetManager.loadThing(THING_ID) } returns flowOf(thing)
    return StarterPackViewModel(
      fleetManager = fleetManager,
      taskDataManager = taskDataManager,
      templateRegistry = BakedInTemplateRegistry(appVersionCode = 1),
      analytics = analytics,
      savedStateHandle = SavedStateHandle(mapOf(Screen.THING_ID to THING_ID)),
    )
  }

  @Test
  fun showingThePackEmitsOfferedOnceWithTheWholeCount() = runTest(dispatcher) {
    val vm = viewModel(pack)
    advanceUntilIdle()

    assertThat(vm.uiState.value.isLoading).isFalse()
    assertThat(vm.uiState.value.items.map { it.selected }).containsExactly(true, true, false).inOrder()
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(1)
    assertThat(analytics.paramsFor("starter_tasks_offered").single())
      .containsAtLeastEntriesIn(mapOf("template_id" to "home", "task_count" to "3"))
    assertThat(analytics.countOf("starter_tasks_accepted")).isEqualTo(0)
  }

  @Test
  fun acceptingWritesTheCheckedOnesAndCountsOnlyThose() = runTest(dispatcher) {
    val vm = viewModel(pack)
    advanceUntilIdle()
    vm.onToggle(0) // drop the filter
    vm.onToggle(2) // keep the septic one after all

    vm.onAccept()
    advanceUntilIdle()

    assertThat(written.map { it.title }).containsExactly("Clean gutters", "Septic pump-out").inOrder()
    // Ordinary cards: the due engine needs a dated TimeRule, and nothing marks them as a pack.
    written.forEach { assertThat(it.rules.single().time_rule?.creation_date).isNotNull() }
    assertThat(analytics.paramsFor("starter_tasks_accepted").single())
      .containsAtLeastEntriesIn(mapOf("template_id" to "home", "task_count" to "2"))
    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(vm.uiState.value.acceptedCount).isEqualTo(2)
  }

  @Test
  fun skippingWritesNothingAndEmitsNoAcceptance() = runTest(dispatcher) {
    val vm = viewModel(pack)
    advanceUntilIdle()

    vm.onSkip()

    assertThat(written).isEmpty()
    coVerify(exactly = 0) { taskDataManager.addTask(any(), any()) }
    assertThat(analytics.countOf("starter_tasks_accepted")).isEqualTo(0)
    assertThat(vm.uiState.value.isDone).isTrue()
  }

  @Test
  fun aFailedWriteDropsOnlyItsOwnCard() = runTest(dispatcher) {
    coEvery { taskDataManager.addTask(THING_ID, match { it.title == "HVAC filter" }) } returns
      Result.failure(IllegalStateException("offline"))
    val vm = viewModel(pack)
    advanceUntilIdle()

    vm.onAccept()
    advanceUntilIdle()

    assertThat(written.map { it.title }).containsExactly("Clean gutters")
    assertThat(analytics.paramsFor("starter_tasks_accepted").single()).containsEntry("task_count", "1")
  }

  @Test
  fun aThingWithNoPackIsNotAnOffer() = runTest(dispatcher) {
    val vm = viewModel(emptyList())
    advanceUntilIdle()

    assertThat(vm.uiState.value.isDone).isTrue()
    assertThat(analytics.countOf("starter_tasks_offered")).isEqualTo(0)
  }

  private companion object {
    const val THING_ID = "thing-1"
  }
}
