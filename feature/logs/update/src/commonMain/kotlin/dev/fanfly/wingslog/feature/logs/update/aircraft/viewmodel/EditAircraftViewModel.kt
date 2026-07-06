package dev.fanfly.wingslog.feature.logs.update.aircraft.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.Propeller
import dev.fanfly.wingslog.aircraft.PropellerBlade
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditAircraftViewModel(
  private val fleetManager: FleetManager, savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState: MutableStateFlow<EditAircraftUiState> =
    MutableStateFlow(EditAircraftUiState())
  val uiState = _uiState.asStateFlow()

  init {
    val aircraftId: String? = savedStateHandle[Screen.AIRCRAFT_ID]
    if (aircraftId.isNullOrEmpty()) {
      logger.i { "Initializing the view model with empty aircraft" }
      loadAircraft(Aircraft())
    } else {
      logger.i { "Loading aircraft $aircraftId" }
      loadAircraftById(aircraftId)
    }
  }

  fun loadAircraftById(id: String) {
    _uiState.update { it.copy(isLoading = true) }
    viewModelScope.launch {
      // We need a way to get one aircraft. FleetManager.loadAircraft returns a Flow.
      // We can take the first emission.
      try {
        fleetManager.loadAircraft(id)
          .collect { aircraft ->
            if (aircraft != null) {
              _uiState.update {
                it.copy(
                  aircraft = aircraft,
                  initialAircraft = it.initialAircraft ?: aircraft,
                  isLoading = false,
                )
              }
            } else {
              // Handle error or not found
              _uiState.update { it.copy(isLoading = false) }
            }
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.w(e) { "Failed to load aircraft by id: $id" }
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  fun loadAircraft(aircraft: Aircraft) {
    _uiState.update {
      it.copy(
        aircraft = aircraft,
        initialAircraft = it.initialAircraft ?: aircraft,
        isLoading = false,
      )
    }
  }

  fun saveAircraft() {
    viewModelScope.launch {
      if (!uiState.value.isValid) {
        _uiState.update { it.copy(showValidationErrors = true) }
        return@launch
      }

      _uiState.update { it.copy(isLoading = true) }
      val result = fleetManager.updateAircraft(uiState.value.aircraft)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaved = true) }
      }
      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun deleteAircraft() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val result = fleetManager.deleteAircraft(uiState.value.aircraft.id)
      if (result.isSuccess) {
        _uiState.update { it.copy(isDeleted = true) }
      }
      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun onMakeChanged(newValue: String) {
    _uiState.update {
      it.copy(
        aircraft = it.aircraft.copy(
          make = newValue.replaceFirstChar { char -> char.uppercase() }
        ))
    }
  }

  fun onModelChanged(newValue: String) {
    _uiState.update {
      it.copy(
        aircraft = it.aircraft.copy(
          model = newValue.replaceFirstChar { char -> char.uppercase() }
        ))
    }
  }

  fun onSerialChanged(newValue: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy(serial = newValue.uppercase())) }
  }

  fun onTailNumberChanged(newValue: String) {
    _uiState.update { it.copy(aircraft = it.aircraft.copy(tail_number = newValue.uppercase())) }
  }

  fun onEngineMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      newEngines[engineIndex] = newEngines[engineIndex].copy(
        make = newValue.replaceFirstChar { char -> char.uppercase() }
      )
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onEngineModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      newEngines[engineIndex] = newEngines[engineIndex].copy(
        model = newValue.replaceFirstChar { char -> char.uppercase() }
      )
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onEngineSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      newEngines[engineIndex] =
        newEngines[engineIndex].copy(serial = newValue.uppercase())
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerHubMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub
        ?: PropellerHub()).copy(make = newValue.replaceFirstChar { char -> char.uppercase() })
      val newPropeller =
        (engine.propeller ?: Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerHubModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub
        ?: PropellerHub()).copy(model = newValue.replaceFirstChar { char -> char.uppercase() })
      val newPropeller =
        (engine.propeller ?: Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerHubSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub ?: PropellerHub()).copy(
        serial = newValue.uppercase()
      )
      val newPropeller =
        (engine.propeller ?: Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerBladeSerialChanged(
    engineIndex: Int,
    bladeIndex: Int,
    newValue: String
  ) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: Propeller()
      val newBlades = propeller.blades.toMutableList()
      if (bladeIndex < newBlades.size) {
        newBlades[bladeIndex] =
          newBlades[bladeIndex].copy(serial = newValue.uppercase())
      }
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onAddBlade(engineIndex: Int) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: Propeller()
      val newBlades = propeller.blades.toMutableList()
      newBlades.add(PropellerBlade())
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onAddEngine() {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      newEngines.add(
        Engine(
          propeller = Propeller(
            blades = listOf(PropellerBlade())
          )
        )
      )
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onRemoveEngine(engineIndex: Int) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      if (engineIndex in newEngines.indices) {
        newEngines.removeAt(engineIndex)
      }
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onRemoveBlade(engineIndex: Int, bladeIndex: Int) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: Propeller()
      val newBlades = propeller.blades.toMutableList()
      if (bladeIndex in newBlades.indices) {
        newBlades.removeAt(bladeIndex)
      }
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  companion object {
    private val logger = Logger.withTag("EditAircraftViewModel")
  }
}
