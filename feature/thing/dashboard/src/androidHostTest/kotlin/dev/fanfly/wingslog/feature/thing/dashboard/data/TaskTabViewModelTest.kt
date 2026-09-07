package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.search.datamanager.impl.SearchEngineImpl
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask
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
class TaskTabViewModelTest {

  private val statusManager: TaskStatusManager = mockk()
  private val fixedClock = object : Clock {
    override fun now() = Instant.parse("2026-09-06T12:00:00Z")
  }

  private val dueSoon = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t1", title = "ELT battery replacement", component = ComponentType.COMPONENT_AIRFRAME),
    DueMetadata(nextDueDate = LocalDate(2026, 10, 1)),
  )
  private val dueLater = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t2", title = "Annual inspection", component = ComponentType.COMPONENT_AIRFRAME),
    DueMetadata(nextDueDate = LocalDate(2027, 3, 14)),
  )
  private val meterOnly = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t3", title = "Oil and filter change", component = ComponentType.COMPONENT_ENGINE),
    DueMetadata(nextDueEngine = 2889f),
  )
  private val complied = MaintenanceTaskWithStatus(
    MaintenanceTask(id = "t4", title = "Fuel selector AD", reference_number = "AD 2011-10-09", component = ComponentType.COMPONENT_AIRFRAME, type = ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE),
    DueMetadata(status = DueStatus.COMPLIED, compliedDate = LocalDate(2026, 3, 14)),
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { statusManager.observeTasksWithStatus(THING_ID) } returns flowOf(listOf(dueSoon, dueLater, meterOnly, complied))
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = TaskTabViewModel(
    statusManager, SearchEngineImpl(), SearchTuning(0, Dispatchers.Unconfined), THING_ID, fixedClock, TimeZone.UTC,
  )
  private fun TaskTabViewModel.active() = uiState.value.activeTasks.map { it.card.id }
  private fun TaskTabViewModel.complied() = uiState.value.completedTasks.map { it.card.id }

  @Test
  fun noFilter_splitsActiveFromComplied_keepingOrder() {
    val vm = viewModel()
    assertThat(vm.active()).containsExactly("t1", "t2", "t3").inOrder()
    assertThat(vm.complied()).containsExactly("t4")
  }

  @Test
  fun window_isDueWithinForActive_andPastForComplied() {
    val vm = viewModel()
    vm.onFilterChange(RecordFilter(time = TimeWindow.LastMonths(3)))
    assertThat(vm.active()).containsExactly("t1", "t3").inOrder()
    assertThat(vm.complied()).isEmpty()
    vm.onFilterChange(RecordFilter(time = TimeWindow.LastMonths(12)))
    assertThat(vm.active()).containsExactly("t1", "t2", "t3").inOrder()
    assertThat(vm.complied()).containsExactly("t4")
  }

  @Test
  fun complianceFacet_narrowsBothSubViews() {
    val vm = viewModel()
    vm.onFilterChange(RecordFilter(facet = Facet.Compliance(ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE)))
    assertThat(vm.active()).isEmpty()
    assertThat(vm.complied()).containsExactly("t4")
  }

  @Test
  fun componentAndQuery() {
    val vm = viewModel()
    vm.onFilterChange(RecordFilter(components = setOf(ComponentType.COMPONENT_ENGINE)))
    assertThat(vm.active()).containsExactly("t3")
    vm.onFilterChange(RecordFilter(query = "2011-10-09"))
    assertThat(vm.active()).isEmpty()
    assertThat(vm.complied()).containsExactly("t4")
    vm.clearFilter()
    assertThat(vm.active()).hasSize(3)
  }
}
