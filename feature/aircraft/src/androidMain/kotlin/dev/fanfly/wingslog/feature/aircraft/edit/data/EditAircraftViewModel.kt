package dev.fanfly.wingslog.feature.aircraft.edit.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.edit.EditAircraftConstants.ARGUMENT_AIRCRAFT_ID
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
        logger.w(e) { "Failed to load aircraft by id: $id" }
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
      newEngines[engineIndex] = newEngines[engineIndex].copy(serial = newValue.uppercase())
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerHubMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub
        ?: dev.fanfly.wingslog.aircraft.PropellerHub()).copy(make = newValue.replaceFirstChar { char -> char.uppercase() })
      val newPropeller =
        (engine.propeller ?: dev.fanfly.wingslog.aircraft.Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerHubModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub
        ?: dev.fanfly.wingslog.aircraft.PropellerHub()).copy(model = newValue.replaceFirstChar { char -> char.uppercase() })
      val newPropeller =
        (engine.propeller ?: dev.fanfly.wingslog.aircraft.Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerHubSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub ?: dev.fanfly.wingslog.aircraft.PropellerHub()).copy(
        serial = newValue.uppercase()
      )
      val newPropeller =
        (engine.propeller ?: dev.fanfly.wingslog.aircraft.Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onPropellerBladeSerialChanged(engineIndex: Int, bladeIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: dev.fanfly.wingslog.aircraft.Propeller()
      val newBlades = propeller.blades.toMutableList()
      if (bladeIndex < newBlades.size) {
        newBlades[bladeIndex] = newBlades[bladeIndex].copy(serial = newValue.uppercase())
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
      val propeller = engine.propeller ?: dev.fanfly.wingslog.aircraft.Propeller()
      val newBlades = propeller.blades.toMutableList()
      newBlades.add(dev.fanfly.wingslog.aircraft.PropellerBlade())
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(aircraft = it.aircraft.copy(engine = newEngines))
    }
  }

  fun onAddEngine() {
    _uiState.update {
      val newEngines = it.aircraft.engine.toMutableList()
      newEngines.add(
        dev.fanfly.wingslog.aircraft.Engine(
          propeller = dev.fanfly.wingslog.aircraft.Propeller(
            blades = listOf(dev.fanfly.wingslog.aircraft.PropellerBlade())
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
      val propeller = engine.propeller ?: dev.fanfly.wingslog.aircraft.Propeller()
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