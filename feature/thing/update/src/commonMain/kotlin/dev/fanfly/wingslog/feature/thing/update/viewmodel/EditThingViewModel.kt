package dev.fanfly.wingslog.feature.thing.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.thing.Engine
import dev.fanfly.wingslog.thing.Propeller
import dev.fanfly.wingslog.thing.PropellerBlade
import dev.fanfly.wingslog.thing.PropellerHub
import dev.fanfly.wingslog.thing.Thing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditThingViewModel(
  private val fleetManager: FleetManager,
  private val sharingManager: SharingManager,
  currentThingTemplate: CurrentThingTemplate,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState: MutableStateFlow<EditThingUiState> =
    MutableStateFlow(
      // Read once at construction: the template of the thing being edited cannot change while the
      // form is open, and a mid-edit change would silently alter what the form accepts.
      EditThingUiState(
        requireSerials = currentThingTemplate.capabilities.value.component_serial_prompt,
      ),
    )
  val uiState = _uiState.asStateFlow()

  init {
    val thingId: String? = savedStateHandle[Screen.AIRCRAFT_ID]
    if (thingId.isNullOrEmpty()) {
      logger.i { "Initializing the view model with empty aircraft" }
      loadThing(Thing())
    } else {
      logger.i { "Loading aircraft $thingId" }
      loadThingById(thingId)
      observeHostedByMe(thingId)
      observeOtherMembers(thingId)
    }
  }

  /**
   * Whether this thing lives in *our* tree. `FleetEntry.shared` is exactly that question asked
   * the other way round, and it is answered from the local refs, so it holds offline too.
   *
   * This is deliberately not read off [ShareRole]: a co-owner is `OWNER` as well, and the thing that
   * separates them from the host is whose tree the thing is in — not what role they hold.
   */
  /** Everyone on the share except us — the people a delete would take the thing away from. */
  private fun observeOtherMembers(thingId: String) {
    viewModelScope.launch {
      sharingManager.observeShareState(thingId)
        .map { share -> share.members.count { !it.isSelf } }
        .distinctUntilChanged()
        .catch { emit(0) } // roster unreadable (unshared thing) — nobody else to warn about
        .collect { count -> _uiState.update { it.copy(otherMemberCount = count) } }
    }
  }

  private fun observeHostedByMe(thingId: String) {
    viewModelScope.launch {
      fleetManager.observeFleetDashboard()
        .map { fleet -> fleet.firstOrNull { it.thing.id == thingId }?.shared == false }
        .distinctUntilChanged()
        .collect { hosted -> _uiState.update { it.copy(hostedByMe = hosted) } }
    }
  }

  fun loadThingById(id: String) {
    _uiState.update { it.copy(isLoading = true) }
    viewModelScope.launch {
      // We need a way to get one thing. FleetManager.loadAircraft returns a Flow.
      // We can take the first emission.
      try {
        fleetManager.loadThing(id)
          .collect { thing ->
            if (thing != null) {
              _uiState.update {
                it.copy(
                  thing = thing,
                  initialAircraft = it.initialAircraft ?: thing,
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

  fun loadThing(thing: Thing) {
    _uiState.update {
      it.copy(
        thing = thing,
        initialAircraft = it.initialAircraft ?: thing,
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
      val result = fleetManager.updateThing(uiState.value.thing)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaved = true) }
      }
      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun deleteThing() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      val result = fleetManager.deleteThing(uiState.value.thing.id)
      if (result.isSuccess) {
        _uiState.update { it.copy(isDeleted = true) }
      }
      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun onMakeChanged(newValue: String) {
    _uiState.update {
      it.copy(
        thing = it.thing.copy(
          make = newValue.replaceFirstChar { char -> char.uppercase() }
        ))
    }
  }

  fun onModelChanged(newValue: String) {
    _uiState.update {
      it.copy(
        thing = it.thing.copy(
          model = newValue.replaceFirstChar { char -> char.uppercase() }
        ))
    }
  }

  fun onSerialChanged(newValue: String) {
    _uiState.update { it.copy(thing = it.thing.copy(serial = newValue.uppercase())) }
  }

  fun onTailNumberChanged(newValue: String) {
    _uiState.update { it.copy(thing = it.thing.copy(tail_number = newValue.uppercase())) }
  }

  fun onEngineMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      newEngines[engineIndex] = newEngines[engineIndex].copy(
        make = newValue.replaceFirstChar { char -> char.uppercase() }
      )
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onEngineModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      newEngines[engineIndex] = newEngines[engineIndex].copy(
        model = newValue.replaceFirstChar { char -> char.uppercase() }
      )
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onEngineSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      newEngines[engineIndex] =
        newEngines[engineIndex].copy(serial = newValue.uppercase())
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onPropellerHubMakeChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub
        ?: PropellerHub()).copy(make = newValue.replaceFirstChar { char -> char.uppercase() })
      val newPropeller =
        (engine.propeller ?: Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onPropellerHubModelChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub
        ?: PropellerHub()).copy(model = newValue.replaceFirstChar { char -> char.uppercase() })
      val newPropeller =
        (engine.propeller ?: Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onPropellerHubSerialChanged(engineIndex: Int, newValue: String) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val newHub = (engine.propeller?.hub ?: PropellerHub()).copy(
        serial = newValue.uppercase()
      )
      val newPropeller =
        (engine.propeller ?: Propeller()).copy(hub = newHub)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onPropellerBladeSerialChanged(
    engineIndex: Int,
    bladeIndex: Int,
    newValue: String
  ) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: Propeller()
      val newBlades = propeller.blades.toMutableList()
      if (bladeIndex < newBlades.size) {
        newBlades[bladeIndex] =
          newBlades[bladeIndex].copy(serial = newValue.uppercase())
      }
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onAddBlade(engineIndex: Int) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: Propeller()
      val newBlades = propeller.blades.toMutableList()
      newBlades.add(PropellerBlade())
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onAddEngine() {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      newEngines.add(
        Engine(
          propeller = Propeller(
            blades = listOf(PropellerBlade())
          )
        )
      )
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onRemoveEngine(engineIndex: Int) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      if (engineIndex in newEngines.indices) {
        newEngines.removeAt(engineIndex)
      }
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  fun onRemoveBlade(engineIndex: Int, bladeIndex: Int) {
    _uiState.update {
      val newEngines = it.thing.engine.toMutableList()
      val engine = newEngines[engineIndex]
      val propeller = engine.propeller ?: Propeller()
      val newBlades = propeller.blades.toMutableList()
      if (bladeIndex in newBlades.indices) {
        newBlades.removeAt(bladeIndex)
      }
      val newPropeller = propeller.copy(blades = newBlades)
      newEngines[engineIndex] = engine.copy(propeller = newPropeller)
      it.copy(thing = it.thing.copy(engine = newEngines))
    }
  }

  companion object {
    private val logger = Logger.withTag("EditAircraftViewModel")
  }
}
