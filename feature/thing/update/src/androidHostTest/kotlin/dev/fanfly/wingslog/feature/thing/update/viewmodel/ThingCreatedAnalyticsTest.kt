package dev.fanfly.wingslog.feature.thing.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.thing.Engine
import dev.fanfly.wingslog.thing.Propeller
import dev.fanfly.wingslog.thing.PropellerBlade
import dev.fanfly.wingslog.thing.PropellerHub
import dev.fanfly.wingslog.thing.Thing
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
 * That `thing_created` is emitted for a create and **not** for an edit.
 *
 * Firebase DebugView (#667) can only show that an event arrived; it cannot show that the branch was
 * right. This is the half that matters for PRD §13: the same form serves create and edit, so an
 * event fired on both paths lands in GA4 looking perfectly healthy while inflating the ≥1.8
 * Things-per-account target with every rename.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThingCreatedAnalyticsTest {

  private val dispatcher = StandardTestDispatcher()
  private val analytics = RecordingAnalyticsManager()
  private val fleetManager = mockk<FleetManager>()
  private val sharingManager = mockk<SharingManager>(relaxed = true)
  private val template = mockk<CurrentThingTemplate>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { template.templateId } returns "airplane"
    every { template.capabilities } returns
      kotlinx.coroutines.flow.MutableStateFlow(CurrentThingTemplate.ALL_ENABLED)
    every { fleetManager.observeFleetDashboard() } returns emptyFlow()
    every { sharingManager.observeShareState(any()) } returns emptyFlow()
    coEvery { fleetManager.updateThing(any()) } returns Result.success(true)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(existingId: String?) = EditThingViewModel(
    fleetManager = fleetManager,
    sharingManager = sharingManager,
    currentThingTemplate = template,
    analytics = analytics,
    savedStateHandle = SavedStateHandle(
      if (existingId == null) emptyMap() else mapOf(Screen.AIRCRAFT_ID to existingId)
    ),
  )

  /** Valid enough to pass `isValid` — the save returns early otherwise and emits nothing. */
  private fun completeThing() = Thing(
    make = "Cessna",
    model = "172",
    serial = "SN-1",
    engine = listOf(
      Engine(
        make = "Lycoming",
        model = "O-320",
        serial = "E-1",
        propeller = Propeller(
          hub = PropellerHub(make = "McCauley", model = "1C160"),
          blades = listOf(PropellerBlade(serial = "B-1")),
        ),
      ),
    ),
  )

  @Test
  fun creatingAThingEmitsThingCreatedWithItsTemplate() = runTest(dispatcher) {
    val vm = viewModel(existingId = null)
    vm.loadThing(completeThing())

    vm.saveAircraft()
    advanceUntilIdle()

    assertThat(analytics.countOf("thing_created")).isEqualTo(1)
    assertThat(analytics.paramsFor("thing_created").single())
      .containsEntry("template_id", "airplane")
  }

  @Test
  fun editingAnExistingThingEmitsNothing() = runTest(dispatcher) {
    every { fleetManager.loadThing("thing-1") } returns flowOf(completeThing())

    val vm = viewModel(existingId = "thing-1")
    advanceUntilIdle()
    vm.loadThing(completeThing())

    vm.saveAircraft()
    advanceUntilIdle()

    assertThat(analytics.countOf("thing_created")).isEqualTo(0)
  }

  @Test
  fun aFailedCreateEmitsNothing() = runTest(dispatcher) {
    // §13 counts Things that exist. A create that the write rejected did not produce one.
    coEvery { fleetManager.updateThing(any()) } returns
      Result.failure(IllegalStateException("offline"))

    val vm = viewModel(existingId = null)
    vm.loadThing(completeThing())

    vm.saveAircraft()
    advanceUntilIdle()

    assertThat(analytics.countOf("thing_created")).isEqualTo(0)
  }

  @Test
  fun anInvalidFormEmitsNothing() = runTest(dispatcher) {
    val vm = viewModel(existingId = null)
    vm.loadThing(Thing()) // blank: isValid is false

    vm.saveAircraft()
    advanceUntilIdle()

    assertThat(analytics.countOf("thing_created")).isEqualTo(0)
  }
}
