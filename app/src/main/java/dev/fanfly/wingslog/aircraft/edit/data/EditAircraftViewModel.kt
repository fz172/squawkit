package dev.fanfly.wingslog.aircraft.edit.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.copy
import dev.fanfly.wingslog.aircraft.manager.AircraftManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditAircraftViewModel @Inject constructor(private val aircraftManager: AircraftManager) :
  ViewModel() {

  private val _uiState: MutableStateFlow<EditAircraftUiState> =
    MutableStateFlow(EditAircraftUiState())
  val uiState = _uiState.asStateFlow()

  fun loadAircraft(aircraft: Aircraft) {
    _uiState.update { it.copy(aircraft = aircraft, isLoading = false) }
  }

  fun saveAircraft() {
    viewModelScope.launch {
      if (!uiState.value.isValid) {
        _uiState.update { it.copy(showValidationErrors = true) }
        return@launch
      }

      _uiState.update { it.copy(isLoading = true) }
      val result = aircraftManager.updateAircraft(uiState.value.aircraft)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaved = true) }
      }
      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun onMakeChanged(newValue: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { make = newValue.replaceFirstChar { char -> char.uppercase() } }) }
  }

  fun onModelChanged(newValue: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { model = newValue.replaceFirstChar { char -> char.uppercase() } }) }
  }

  fun onSerialChanged(newValue: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { serial = newValue.uppercase() }) }
  }

  fun onTailNumberChanged(newValue: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy { tailNumber = newValue.uppercase() }) }
  }

  fun onEngineMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy { make = newValue.replaceFirstChar { char -> char.uppercase() } }
      })
    }
  }

  fun onEngineModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy { model = newValue.replaceFirstChar { char -> char.uppercase() } }
      })
    }
  }

  fun onEngineSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy { serial = newValue.uppercase() }
      })
    }
  }

  fun onPropellerHubMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          propeller = propeller.copy {
            hub = hub.copy { make = newValue.replaceFirstChar { char -> char.uppercase() } }
          }
        }
      })
    }
  }

  fun onPropellerHubModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          propeller = propeller.copy {
            hub = hub.copy { model = newValue.replaceFirstChar { char -> char.uppercase() } }
          }
        }
      })
    }
  }

  fun onPropellerHubSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          propeller = propeller.copy {
            hub = hub.copy { serial = newValue.uppercase() }
          }
        }
      })
    }
  }


  fun onPropellerBladeSerialChanged(engineIndex: Int, bladeIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          propeller = propeller.copy {
            blades[bladeIndex] = blades[bladeIndex].copy { serial = newValue.uppercase() }
          }
        }
      })
    }
  }

  fun onAddBlade(engineIndex: Int) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          propeller = propeller.copy {
            blades += dev.fanfly.wingslog.aircraft.propellerBlade { }
          }
        }
      })
    }
  }

  fun onAddEngine() {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine += dev.fanfly.wingslog.aircraft.engine {
          propeller = dev.fanfly.wingslog.aircraft.propeller {
            blades += dev.fanfly.wingslog.aircraft.propellerBlade { }
          }
        }
      })
    }
  }

  fun onRemoveEngine(engineIndex: Int) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        val keptEngines = engine.toList().filterIndexed { index, _ -> index != engineIndex }
        engine.clear()
        engine.addAll(keptEngines)
      })
    }
  }

  fun onRemoveBlade(engineIndex: Int, bladeIndex: Int) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          propeller = propeller.copy {
            val keptBlades = blades.toList().filterIndexed { index, _ -> index != bladeIndex }
            blades.clear()
            blades.addAll(keptBlades)
          }
        }
      })
    }
  }
}