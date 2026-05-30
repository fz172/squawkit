package dev.fanfly.wingslog.feature.fleet.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.ui.shell.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.shell.ShellAircraft
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the ambient aircraft selection for the adaptive shell ([AdaptiveAppShell]).
 *
 * Per the redesign, the selected aircraft is app-level state chosen from the switcher rather than a
 * navigation argument carried per destination — see `docs/web/web_adaptive_layout_design.html` §6.
 * Lives in fleet/viewing because both hosts (composeApp + webApp) already wire this module and it has
 * access to [FleetManager]; it may move to a dedicated shell module as the shell grows (M2/M3).
 */
class AdaptiveShellViewModel(
  fleetManager: FleetManager,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AdaptiveShellUiState())
  val uiState: StateFlow<AdaptiveShellUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      fleetManager.observeFleetDashboard().collect { fleet ->
        _uiState.update { state ->
          val mapped = fleet.map { ac ->
            ShellAircraft(
              id = ac.id,
              tail = ac.tail_number,
              name = listOf(ac.make, ac.model).filter { it.isNotBlank() }.joinToString(" "),
            )
          }
          // Keep the current selection if it still exists, otherwise fall back to the first aircraft.
          val selected = state.selectedAircraftId
            ?.takeIf { id -> mapped.any { it.id == id } }
            ?: mapped.firstOrNull()?.id
          state.copy(aircraft = mapped, selectedAircraftId = selected)
        }
      }
    }
  }

  fun selectAircraft(id: String) {
    _uiState.update { it.copy(selectedAircraftId = id) }
  }
}
