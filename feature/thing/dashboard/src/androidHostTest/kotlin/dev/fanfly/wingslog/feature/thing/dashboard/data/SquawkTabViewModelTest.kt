package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Squawk
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
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val THING_ID = "thing-1"

@OptIn(ExperimentalCoroutinesApi::class)
class SquawkTabViewModelTest {

  private val squawkManager: SquawkManager = mockk()
  private val logManager: MaintenanceLogManager = mockk()
  private val fixedClock = object : Clock {
    override fun now() = Instant.parse("2026-09-06T12:00:00Z")
  }

  private fun at(date: String) = toWireInstant(Instant.parse("${date}T12:00:00Z").epochSeconds)

  private val openXpdr = Squawk(id = "s1", title = "Transponder intermittent", component_type = ComponentType.COMPONENT_AIRFRAME, created_at = at("2026-08-22"))
  private val openOil = Squawk(id = "s2", title = "Oil seep at magneto", component_type = ComponentType.COMPONENT_ENGINE, created_at = at("2026-08-30"))
  private val addressedElt = Squawk(id = "s3", title = "ELT self-test fails", component_type = ComponentType.COMPONENT_AIRFRAME, created_at = at("2025-04-02"), addressed_by_log_id = "l5")
  private val eltLog = MaintenanceLog(id = "l5", timestamp = at("2026-04-05"), work_description = "Replaced ELT battery")

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { squawkManager.observeSquawks(THING_ID) } returns flowOf(listOf(openXpdr, openOil, addressedElt))
    every { logManager.observeLogs(THING_ID) } returns flowOf(listOf(eltLog))
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = SquawkTabViewModel(
    squawkManager, logManager, SearchEngineImpl(), THING_ID, fixedClock, TimeZone.UTC,
    queryDebounceMillis = 0, searchDispatcher = Dispatchers.Unconfined,
  )
  private fun SquawkTabViewModel.ids() = uiState.value.squawks.map { it.squawk.id }

  @Test
  fun noFilter_listsEverySquawkWithStatus() {
    val vm = viewModel()
    assertThat(vm.ids()).containsExactly("s1", "s2", "s3").inOrder()
    assertThat(vm.uiState.value.squawks.map { it.status.name }).containsExactly("OPEN", "OPEN", "ADDRESSED").inOrder()
  }

  @Test
  fun query_andComponent_narrow() {
    val vm = viewModel()
    vm.onFilterChange(RecordFilter(query = "transponder"))
    assertThat(vm.ids()).containsExactly("s1")
    vm.onFilterChange(RecordFilter(components = setOf(ComponentType.COMPONENT_ENGINE)))
    assertThat(vm.ids()).containsExactly("s2")
  }

  @Test
  fun addressedSquawk_datesFromItsLog() {
    val vm = viewModel()
    // Created 2025-04, addressed 2026-04: inside a 12-month window only through the log date.
    vm.onFilterChange(RecordFilter(time = TimeWindow.LastMonths(12)))
    assertThat(vm.ids()).containsExactly("s1", "s2", "s3")
    vm.onFilterChange(RecordFilter(time = TimeWindow.LastMonths(3)))
    assertThat(vm.ids()).containsExactly("s1", "s2")
  }

  @Test
  fun clearFilter_restoresEverything() {
    val vm = viewModel()
    vm.onFilterChange(RecordFilter(query = "nothing"))
    assertThat(vm.ids()).isEmpty()
    vm.clearFilter()
    assertThat(vm.uiState.value.filter).isEqualTo(RecordFilter())
    assertThat(vm.ids()).hasSize(3)
  }
}
