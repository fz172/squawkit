package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class SquawkFormState(
  val aircraftId: String = "",
  val squawkId: String? = null,
  val title: String = "",
  val description: String = "",
  val priority: SquawkPriority = SquawkPriority.SQUAWK_PRIORITY_LOW,
  val component: ComponentType = ComponentType.COMPONENT_UNKNOWN,
  val titleError: Boolean = false,
  val isSaving: Boolean = false,
  val isAddressedReadOnly: Boolean = false,
  val reportedDateFormatted: String = "",
  val addressedByLogId: String = "",
)

sealed interface SquawkFormEvent {
  data object NavigateBack : SquawkFormEvent
  data class SaveSuccess(val message: String) : SquawkFormEvent
  data class NavigateToLog(val aircraftId: String, val logId: String) : SquawkFormEvent
}

class SquawkFormViewModel(
  private val squawkManager: SquawkManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String = checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])
  private val squawkId: String? = savedStateHandle[Screen.SQUAWK_ID]

  private val _state = MutableStateFlow(SquawkFormState(aircraftId = aircraftId, squawkId = squawkId))
  val state: StateFlow<SquawkFormState> = _state.asStateFlow()

  private val _events = Channel<SquawkFormEvent>()
  val events = _events.receiveAsFlow()

  init {
    if (squawkId != null) loadExisting(squawkId)
  }

  private fun loadExisting(id: String) {
    viewModelScope.launch {
      squawkManager.observeSquawks(aircraftId).collect { squawks ->
        val squawk = squawks.find { it.id == id } ?: return@collect
        _state.update {
          it.copy(
            title = squawk.title,
            description = squawk.description,
            priority = squawk.priority,
            component = squawk.component_type,
            isAddressedReadOnly = squawk.addressed_by_log_id.isNotEmpty(),
            reportedDateFormatted = squawk.created_at?.toLocalDate()?.toDisplayFormat() ?: "",
            addressedByLogId = squawk.addressed_by_log_id,
          )
        }
      }
    }
  }

  fun onTitleChange(value: String) = _state.update { it.copy(title = value, titleError = false) }
  fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
  fun onPriorityChange(value: SquawkPriority) = _state.update { it.copy(priority = value) }
  fun onComponentChange(value: ComponentType) = _state.update { it.copy(component = value) }

  fun onViewLog() {
    val current = _state.value
    val logId = current.addressedByLogId.takeIf { it.isNotEmpty() } ?: return
    viewModelScope.launch { _events.send(SquawkFormEvent.NavigateToLog(current.aircraftId, logId)) }
  }

  fun save(onSuccessMessage: String) {
    val current = _state.value
    if (current.title.isBlank()) {
      _state.update { it.copy(titleError = true) }
      return
    }
    _state.update { it.copy(isSaving = true) }
    viewModelScope.launch {
      val squawk = Squawk(
        id = current.squawkId ?: generateRandomId(),
        title = current.title.trim(),
        description = current.description.trim(),
        priority = current.priority,
        component_type = current.component,
        created_at = if (current.squawkId == null) Clock.System.now().toWireInstant() else null,
      )
      val result = if (current.squawkId == null)
        squawkManager.addSquawk(aircraftId, squawk)
      else
        squawkManager.updateSquawk(aircraftId, squawk)

      _state.update { it.copy(isSaving = false) }
      result.onSuccess { _events.send(SquawkFormEvent.SaveSuccess(onSuccessMessage)) }
    }
  }

  fun onBack() {
    viewModelScope.launch { _events.send(SquawkFormEvent.NavigateBack) }
  }

  private fun todayFormatted(): String =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toDisplayFormat()
}
