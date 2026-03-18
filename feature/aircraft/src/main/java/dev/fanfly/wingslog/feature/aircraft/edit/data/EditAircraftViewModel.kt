package dev.fanfly.wingslog.feature.aircraft.edit.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.flogger.FluentLogger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.aircraft
import dev.fanfly.wingslog.aircraft.copy
import dev.fanfly.wingslog.feature.aircraft.edit.EditAircraftConstants.ARGUMENT_AIRCRAFT_ID
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditAircraftViewModel(
  private val aircraftManager: AircraftManager, savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val _uiState: MutableStateFlow<EditAircraftUiState> =
    MutableStateFlow(EditAircraftUiState())
  val uiState = _uiState.asStateFlow()

  init {
    val aircraftId: String? = savedStateHandle[ARGUMENT_AIRCRAFT_ID]
    if (aircraftId.isNullOrEmpty()) {
      logger.atInfo().log("Initializing the view model with empty aircraft")
      loadAircraft(aircraft {})
    } else {
      logger.atInfo().log("Loading aircraft %s", aircraftId)
      loadAircraftById(aircraftId)
    }
  }

  fun loadAircraftById(id: String) {
    _uiState.update { it.copy(isLoading = true) }
    viewModelScope.launch {
      // We need a way to get one aircraft. AircraftManager.loadAircraft returns a Flow.
      // We can take the first emission.
      try {
        aircraftManager.loadAircraft(id).collect { aircraft ->
          if (aircraft != null) {
            _uiState.update { it.copy(aircraft = aircraft, isLoading = false) }
          } else {
            // Handle error or not found
            _uiState.update { it.copy(isLoading = false) }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.atWarning().withCause(e).log("Failed to load aircraft by id: %s", id)
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

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
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        make = newValue.replaceFirstChar { char -> char.uppercase() }
      })
    }
  }

  fun onModelChanged(newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        model = newValue.replaceFirstChar { char -> char.uppercase() }
      })
    }
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
        engine[engineIndex] =
          engine[engineIndex].copy { make = newValue.replaceFirstChar { char -> char.uppercase() } }
      })
    }
  }

  fun onEngineModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      it.copy(aircraft = it.aircraft.copy {
        engine[engineIndex] = engine[engineIndex].copy {
          model = newValue.replaceFirstChar { char -> char.uppercase() }
        }
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

  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()
  }
}