package dev.fanfly.wingslog.fleet.manage.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.copy
import dev.fanfly.wingslog.fleet.manager.AircraftManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageAircraftViewModel @Inject constructor(private val aircraftManager: AircraftManager) :
  ViewModel() {

  private val _uiState: MutableStateFlow<ManageAircraftUiState> =
    MutableStateFlow(ManageAircraftUiState())
  val uiState = _uiState.asStateFlow()

  fun loadAircraft(aircraft: Aircraft) {
    _uiState.update { it.copy(aircraft = aircraft, isLoading = false) }
  }

  fun saveAircraft() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val result = aircraftManager.updateAircraft(uiState.value.aircraft)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaved = true) }
      }
      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun onMakeChanged(newMake: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { make = newMake }) }
  }

  fun onModelChanged(newModel: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { model = newModel }) }
  }

  fun onSerialChanged(newSerial: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { serial = newSerial }) }
  }
}