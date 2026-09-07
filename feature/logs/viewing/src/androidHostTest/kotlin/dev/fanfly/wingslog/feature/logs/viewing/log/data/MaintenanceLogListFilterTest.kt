package dev.fanfly.wingslog.feature.logs.viewing.log.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Technician
import dev.gitlive.firebase.auth.FirebaseAuth
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val THING_ID = "thing-1"

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceLogListFilterTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val logManager: MaintenanceLogManager = mockk(relaxed = true)
  private val tasks: TaskDataManager = mockk(relaxed = true)
  private val sharing: SharingManager = mockk(relaxed = true)
  private val technicians: TechnicianManager = mockk(relaxed = true)
  private val squawks: SquawkManager = mockk(relaxed = true)
  private val auth: FirebaseAuth = mockk(relaxed = true)

  private val fixedClock = object : Clock {
    override fun now() = Instant.parse("2026-09-06T12:00:00Z")
  }

  private fun log(id: String, date: String, description: String, component: ComponentType, tech: String? = null) = MaintenanceLog(
    id = id,
    timestamp = Instant.parse("${date}T12:00:00Z").let { toWireInstant(it.epochSeconds) },
    work_description = description,
    component_type = component,
    technician = tech?.let { Technician(id = it, name = it) },
  )

  private val gasket = log("gasket", "2026-09-01", "Replaced left magneto base gasket", ComponentType.COMPONENT_ENGINE, tech = "R. Alvarez")
  private val xpdr = log("xpdr", "2026-08-25", "Installed GTX 335 transponder", ComponentType.COMPONENT_AIRFRAME, tech = "Sky Harbor Avionics")
  private val oil = log("oil", "2025-07-30", "Oil and filter change", ComponentType.COMPONENT_ENGINE)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { logManager.observeLogs(THING_ID) } returns flowOf(listOf(oil, gasket, xpdr))
    every { logManager.observeLogAuthors(THING_ID) } returns flowOf(emptyMap())
    every { tasks.observeTasks(THING_ID) } returns flowOf(emptyList())
    every { sharing.observeLinkedTechnicians(THING_ID) } returns flowOf(emptyList())
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
    thingId = THING_ID,
    clock = fixedClock,
    timeZone = TimeZone.UTC,
  )

  private fun MaintenanceLogListViewModel.ids() =
    (uiState.value as MaintenanceLogListUiState.Success).logs.map { it.id }

  private fun MaintenanceLogListViewModel.success() = uiState.value as MaintenanceLogListUiState.Success

  @Test
  fun noFilter_newestFirstWithTotal() {
    val vm = viewModel()
    assertThat(vm.ids()).containsExactly("gasket", "xpdr", "oil").inOrder()
    assertThat(vm.success().totalCount).isEqualTo(3)
    assertThat(vm.success().filter.isActive).isFalse()
  }

  @Test
  fun query_narrowsAndKeepsTotal() {
    val vm = viewModel()
    vm.onSearchQueryChange("transponder")
    assertThat(vm.ids()).containsExactly("xpdr")
    assertThat(vm.success().totalCount).isEqualTo(3)
  }

  @Test
  fun componentToggle_addsThenRemoves() {
    val vm = viewModel()
    vm.onComponentFilterToggle(ComponentType.COMPONENT_ENGINE)
    assertThat(vm.ids()).containsExactly("gasket", "oil").inOrder()
    vm.onComponentFilterToggle(ComponentType.COMPONENT_ENGINE)
    assertThat(vm.ids()).hasSize(3)
  }

  @Test
  fun timeWindow_filtersByWorkDate() {
    val vm = viewModel()
    vm.onTimeWindowChange(TimeWindow.LastMonths(3))
    assertThat(vm.ids()).containsExactly("gasket", "xpdr").inOrder()
    vm.onTimeWindowChange(TimeWindow.Custom(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)))
    assertThat(vm.ids()).containsExactly("oil")
  }

  @Test
  fun technicianFacet_listsNamesAndNarrows() {
    val vm = viewModel()
    assertThat(vm.success().technicians).containsExactly("R. Alvarez", "Sky Harbor Avionics").inOrder()
    vm.onFacetToggle(Facet.Technician("R. Alvarez"))
    assertThat(vm.ids()).containsExactly("gasket")
    vm.onFacetToggle(Facet.Technician("Sky Harbor Avionics"))
    assertThat(vm.ids()).containsExactly("gasket", "xpdr").inOrder()
    vm.onFacetToggle(Facet.Technician("Sky Harbor Avionics"))
    vm.onFacetToggle(Facet.Technician("R. Alvarez"))
    assertThat(vm.ids()).hasSize(3)
  }

  @Test
  fun clearFilter_resetsEverything() {
    val vm = viewModel()
    vm.onSearchQueryChange("oil")
    vm.onComponentFilterToggle(ComponentType.COMPONENT_ENGINE)
    vm.onTimeWindowChange(TimeWindow.LastMonths(12))
    assertThat(vm.ids()).isEmpty()
    vm.clearFilter()
    assertThat(vm.success().filter).isEqualTo(RecordFilter())
    assertThat(vm.ids()).hasSize(3)
  }
}
