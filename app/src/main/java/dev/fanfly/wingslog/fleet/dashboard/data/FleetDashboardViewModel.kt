package dev.fanfly.wingslog.fleet.dashboard.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.manager.MaintenanceLogManager
import dev.fanfly.wingslog.fleet.dashboard.manager.FleetDashboardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FleetDashboardViewModel @Inject constructor(
  private val fleetDashboardManager: FleetDashboardManager,
  private val logManager: MaintenanceLogManager,
) : ViewModel() {

  private var fleetInfoListener: ListenerRegistration? = null

  private val _uiState: MutableStateFlow<FleetDashboardUiState> =
    MutableStateFlow(FleetDashboardUiState(isLoading = true))
  val uiState = _uiState.asStateFlow()

  init {
    // Load the user's fleet when the ViewModel is created
    loadFleetData()
  }

  private fun loadFleetData() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      fleetInfoListener = fleetDashboardManager.observeFleetDashboard { result: List<Aircraft> ->
        _uiState.update {
          it.copy(
            fleet = result, isLoading = false
          )
        }
      }
    }
  }
}