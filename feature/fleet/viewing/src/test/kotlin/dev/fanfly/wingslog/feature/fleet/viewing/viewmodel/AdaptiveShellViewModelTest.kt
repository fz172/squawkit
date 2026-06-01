package dev.fanfly.wingslog.feature.fleet.viewing.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.shell.ShellSection
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class AdaptiveShellViewModelTest {

  private val fleet = MutableStateFlow<List<Aircraft>>(emptyList())
  private val fleetManager = object : FleetManager {
    override fun observeFleetDashboard(): Flow<List<Aircraft>> = fleet
    override suspend fun updateAircraft(aircraft: Aircraft) = Result.success(true)
    override fun loadAircraft(id: String): Flow<Aircraft?> = MutableStateFlow(null)
    override suspend fun deleteAircraft(id: String) = Result.success(true)
  }

  @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

  @After fun tearDown() = Dispatchers.resetMain()

  private fun aircraft(id: String, tail: String, make: String = "Cessna", model: String = "172") =
    Aircraft(id = id, make = make, model = model, tail_number = tail)

  @Test
  fun mapsFleetAndSelectsFirstByDefault() = runTest {
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = AdaptiveShellViewModel(fleetManager)
    runCurrent()

    val s = vm.uiState.value
    assertThat(s.aircraft.map { it.tail }).containsExactly("N1", "N2").inOrder()
    assertThat(s.aircraft.first().name).isEqualTo("Cessna 172")
    assertThat(s.selectedAircraftId).isEqualTo("a1")
    assertThat(s.section).isEqualTo(ShellSection.DASHBOARD)
    assertThat(s.entered).isFalse()
  }

  @Test
  fun keepsSelectionAcrossReemissionWhenStillPresent() = runTest {
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = AdaptiveShellViewModel(fleetManager)
    runCurrent()
    vm.selectAircraft("a2")

    // Re-emit with the same aircraft; the explicit selection must survive.
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    runCurrent()
    assertThat(vm.uiState.value.selectedAircraftId).isEqualTo("a2")

    // Remove the selected one; selection falls back to the first remaining.
    fleet.value = listOf(aircraft("a1", "N1"))
    runCurrent()
    assertThat(vm.uiState.value.selectedAircraftId).isEqualTo("a1")
  }

  @Test
  fun enterAircraftSetsEnteredAndResetsToDashboard() = runTest {
    fleet.value = listOf(aircraft("a1", "N1"), aircraft("a2", "N2"))
    val vm = AdaptiveShellViewModel(fleetManager)
    runCurrent()
    vm.selectSection(ShellSection.LOGS)

    vm.enterAircraft("a2")
    val s = vm.uiState.value
    assertThat(s.entered).isTrue()
    assertThat(s.selectedAircraftId).isEqualTo("a2")
    assertThat(s.section).isEqualTo(ShellSection.DASHBOARD)

    vm.exitToFleet()
    assertThat(vm.uiState.value.entered).isFalse()
  }

  @Test
  fun selectSectionUpdatesSection() = runTest {
    val vm = AdaptiveShellViewModel(fleetManager)
    runCurrent()
    vm.selectSection(ShellSection.SQUAWKS)
    assertThat(vm.uiState.value.section).isEqualTo(ShellSection.SQUAWKS)
  }

  @Test
  fun openSettingsSelectsSettingsAndEnters() = runTest {
    val vm = AdaptiveShellViewModel(fleetManager)
    runCurrent()

    vm.openSettings()
    val s = vm.uiState.value
    assertThat(s.section).isEqualTo(ShellSection.SETTINGS)
    // entered so COMPACT shows the section view (with bottom bar) instead of the fleet landing.
    assertThat(s.entered).isTrue()
  }
}
