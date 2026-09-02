package dev.fanfly.wingslog.feature.thing.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.ThingCreated
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.ComponentField
import dev.fanfly.wingslog.core.template.ComponentPath
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.addComponent
import dev.fanfly.wingslog.core.template.ensureComponentAt
import dev.fanfly.wingslog.core.template.newComponentFor
import dev.fanfly.wingslog.core.template.removeComponentAt
import dev.fanfly.wingslog.core.template.updateComponentAt
import dev.fanfly.wingslog.core.template.with
import dev.fanfly.wingslog.core.template.withSpec
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.thing.ComponentSlot
import dev.fanfly.wingslog.thing.SpecField
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
  private val currentThingTemplate: CurrentThingTemplate,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val _uiState: MutableStateFlow<EditThingUiState> =
    MutableStateFlow(
      // Read once at construction: the template of the thing being edited cannot change while the
      // form is open, and a mid-edit change would silently alter what the form accepts.
      // forThingWithFallback, not the selected template: on a create nothing is selected, and a
      // null template would render a form with no fields and validate nothing. This resolves the
      // same way FleetManagerImpl does on write, so the form asks for exactly what the Thing will
      // be saved under. #739 replaces the fallback with the picker's choice.
      EditThingUiState().withThing(Thing()),
    )
  val uiState = _uiState.asStateFlow()

  /**
   * Applies [thing] and the template it resolves under, **without disturbing anything else**.
   *
   * A copy rather than a fresh state: `hostedByMe` and `otherMemberCount` arrive from their own
   * collectors and may land before or after the load. Rebuilding the state here reset them to
   * their defaults, and `hostedByMe = false` is what hides Delete.
   */
  private fun EditThingUiState.withThing(thing: Thing): EditThingUiState {
    val template = templateRegistry.forThingWithFallback(thing)
    return copy(
      thing = thing,
      template = template,
      requireSerials = template.capabilities?.component_serial_prompt ?: true,
    )
  }

  /**
   * A create rather than an edit. Read once from the route argument, because the same form serves
   * both and [saveAircraft] cannot tell them apart afterwards — by then the thing has an id either
   * way, and counting an edit as a create would inflate the §13 Things-per-account metric.
   */
  private val isNewThing: Boolean =
    savedStateHandle.get<String>(Screen.AIRCRAFT_ID)
      .isNullOrEmpty()

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
                // The loaded thing carries its own DNA, so the template resolves from it rather
                // than from whatever was selected in the shell.
                it.withThing(thing)
                  .copy(
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
      it.withThing(thing)
        .copy(
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
        // Only on the write actually landing: a create that failed is not a Thing, and §13 counts
        // Things that exist.
        if (isNewThing) {
          analytics.log(
            ThingCreated(
              templateId = currentThingTemplate.templateId,
              source = SOURCE_FORM
            )
          )
        }
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

  // The form edits spec and the component tree directly (#668), and both are driven by what the
  // template declares rather than by airplane knowledge (#729).

  /**
   * A spec field the template declares — make, model, VIN, address.
   *
   * **Spec is the only home for identity now.** make/model/serial used to be written twice, to
   * `spec` and onto the airframe component, with `spec` authoritative and a helper keeping them in
   * step. The duplicate existed because the retired proto carried those fields on the airframe; it
   * has no reason to outlive them, and a second copy that nothing reads is a copy that will drift.
   */
  fun onSpecChanged(key: String, newValue: String) {
    _uiState.update {
      it.copy(
        thing = it.thing.withSpec(
          key,
          normalise(key, newValue)
        )
      )
    }
  }

  /** A field on the component at [path], wherever in the tree that is. */
  fun onComponentFieldChanged(
    path: ComponentPath,
    field: ComponentField,
    newValue: String,
  ) {
    val value = if (field == ComponentField.SERIAL) {
      newValue.uppercase()
    } else {
      newValue.replaceFirstChar { it.uppercase() }
    }
    _uiState.update { state ->
      // ensureComponentAt first: a fixed slot renders a row before anything is stored, so the
      // first keystroke into an empty one has to create it and its ancestors.
      state.copy(
        thing = state.thing.ensureComponentAt(path)
          .updateComponentAt(path) { it.with(field, value) },
      )
    }
  }

  /**
   * One of the slot's own declared fields — a tyre's position or its normal pressure.
   *
   * Separate from [onComponentFieldChanged] because these are stored in the component's `spec` bag
   * rather than as named fields, and because the casing differs: a number is left exactly as
   * typed, where make and model get a capital first letter.
   */
  fun onComponentSpecChanged(
    path: ComponentPath,
    field: SpecField,
    newValue: String,
  ) {
    val value = if (field.numeric) newValue else newValue.replaceFirstChar { it.uppercase() }
    _uiState.update { state ->
      // ensureComponentAt first, for the same reason the named fields do it: a fixed slot renders
      // a row before anything is stored.
      state.copy(
        thing = state.thing.ensureComponentAt(path)
          .updateComponentAt(path) { it.withSpec(field.key, value) },
      )
    }
  }

  /** Adds another [slot] under [parentPath]. Only repeatable slots reach here — see `addableSlotsUnder`. */
  fun onAddComponent(parentPath: ComponentPath, slot: ComponentSlot) {
    _uiState.update { state ->
      state.copy(
        thing = state.thing.ensureComponentAt(parentPath)
          .addComponent(parentPath, newComponentFor(slot)),
      )
    }
  }

  fun onRemoveComponent(path: ComponentPath) {
    _uiState.update { it.copy(thing = it.thing.removeComponentAt(path)) }
  }

  /**
   * Serials are upper-cased, everything else gets a capital first letter.
   *
   * Keyed by the conventional spec keys rather than by a template-declared flag: `SpecField` has no
   * casing hint, and inventing one for this would be a schema change for a typing convenience. A
   * key no preset declares falls through untouched, which is the right default for an address.
   */
  private fun normalise(key: String, value: String): String = when (key) {
    SpecKeys.SERIAL, SpecKeys.TAIL_NUMBER -> value.uppercase()
    SpecKeys.MAKE, SpecKeys.MODEL -> value.replaceFirstChar { it.uppercase() }
    else -> value
  }

  companion object {
    private val logger = Logger.withTag("EditAircraftViewModel")

    /**
     * How the Thing was created. Only the manual form exists today; the template picker and any
     * future import path get their own value, so a low non-airplane share in §13 can be read as
     * "not offered" rather than "offered and declined".
     */
    private const val SOURCE_FORM = "form"
  }
}
