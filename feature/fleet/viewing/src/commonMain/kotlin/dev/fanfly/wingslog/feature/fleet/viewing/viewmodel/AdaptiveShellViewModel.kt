package dev.fanfly.wingslog.feature.fleet.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.ui.shell.AdaptiveShellUiState
import dev.fanfly.wingslog.core.ui.shell.ShellAircraft
import dev.fanfly.wingslog.core.ui.shell.ShellSection
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
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
  private val technicianManager: TechnicianManager,
  private val authManager: AuthManager,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AdaptiveShellUiState())
  val uiState: StateFlow<AdaptiveShellUiState> = _uiState.asStateFlow()

  init {
    observeSelf()
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

  /** Current user's name + photo for the sidebar account/settings entry. */
  private fun observeSelf() {
    viewModelScope.launch {
      technicianManager.observeSelf().collect { self ->
        val user = authManager.getCurrentUser()
        _uiState.update {
          it.copy(
            accountPhotoUrl = user?.photoURL,
            accountName = self?.name?.takeIf { name -> name.isNotBlank() }
              ?: user?.displayName?.takeIf { name -> name.isNotBlank() }
              ?: user?.email?.takeIf { email -> email.isNotBlank() },
          )
        }
      }
    }
  }

  /** Switcher selection (above phone): swaps the ambient aircraft in place. */
  fun selectAircraft(id: String) {
    _uiState.update { it.copy(selectedAircraftId = id) }
  }

  /** Switches the active top-level section. */
  fun selectSection(section: ShellSection) {
    _uiState.update { it.copy(section = section) }
  }

  /** Phone: open an aircraft from the fleet landing, resetting to its Dashboard section. */
  fun enterAircraft(id: String) {
    _uiState.update {
      it.copy(selectedAircraftId = id, entered = true, section = ShellSection.DASHBOARD)
    }
  }

  /** Phone: return from a section back to the fleet landing. */
  fun exitToFleet() {
    _uiState.update { it.copy(entered = false) }
  }

  /**
   * Open the global Settings section in the shell. Sets `entered` so that on COMPACT (where the
   * fleet landing is the root) the section view with its bottom bar is shown instead of the landing.
   */
  fun openSettings() {
    _uiState.update { it.copy(section = ShellSection.SETTINGS, entered = true) }
  }
}
