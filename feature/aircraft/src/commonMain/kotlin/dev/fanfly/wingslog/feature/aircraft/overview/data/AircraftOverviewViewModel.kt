package dev.fanfly.wingslog.feature.aircraft.overview.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.database.AircraftManager
import dev.fanfly.wingslog.feature.aircraft.database.InspectionManager
import dev.fanfly.wingslog.feature.aircraft.database.MaintenanceLogManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AircraftOverviewViewModel(
  private val aircraftManager: AircraftManager,
  private val logManager: MaintenanceLogManager,
  private val inspectionManager: InspectionManager,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])

  private val _uiState = MutableStateFlow<AircraftOverviewUiState>(AircraftOverviewUiState.Loading)
  val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()

  private val _events = Channel<AircraftOverviewEvent>()
  val events = _events.receiveAsFlow()

  /** Cached logs — kept in sync by the combine flow, used for detail sheet filtering. */
  private var cachedLogs: List<MaintenanceLog> = emptyList()

  init {
    loadAircraftAndStats()
  }

  private fun loadAircraftAndStats() {
    viewModelScope.launch {
      _uiState.update { AircraftOverviewUiState.Loading }
      combine(
        aircraftManager.loadAircraft(aircraftId),
        logManager.observeLogs(aircraftId),
        inspectionManager.observeInspections(aircraftId)
      ) { aircraft, logs, inspectionCards ->
        cachedLogs = logs
        if (aircraft != null) {
          val currentTachTime = logs.filter { it.tach_time > 0.0 }.maxOfOrNull { it.tach_time }
          val currentAirframeTime =
            logs.filter { it.airframe_time > 0.0 }.maxOfOrNull { it.airframe_time }
          val currentPropTime = logs.filter { it.prop_time > 0.0 }.maxOfOrNull { it.prop_time }
          val stats = LogStats(
            total = logs.size.toLong(),
            airframe = logs.count { it.component_type == MaintenanceLog.ComponentType.AIRFRAME }
              .toLong(),
            engine = logs.count { it.component_type == MaintenanceLog.ComponentType.ENGINE }
              .toLong(),
            propeller = logs.count { it.component_type == MaintenanceLog.ComponentType.PROPELLER }
              .toLong(),
            currentTachTime = currentTachTime,
            currentAirframeTime = currentAirframeTime,
            currentPropTime = currentPropTime
          )
          // Compute due status for each inspection card
          val cardsWithStatus = inspectionCards.map { card ->
            InspectionCardWithStatus(
              card = card,
              dueStatus = inspectionManager.computeNextDue(card, logs),
            )
          }
          val current = _uiState.value as? AircraftOverviewUiState.Success
          val showSheet = current?.showAddInspectionSheet ?: false
          // Refresh selected inspection due status if detail sheet is open
          val refreshedSelected = current?.selectedInspection?.let { sel ->
            cardsWithStatus.find { it.card.id == sel.card.id }
          }
          val refreshedDetailLogs = refreshedSelected?.let { sel ->
            logs.filter { sel.card.id in it.inspection_ids }
              .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
          } ?: emptyList()
          // Preserve editing and deletion states across Firestore real-time updates so that
          // in-flight edits (e.g. force-override inputs) are not wiped when the listener fires.
          val refreshedEditing = current?.editingInspection?.let { editing ->
            cardsWithStatus.find { it.card.id == editing.card.id }
          }
          AircraftOverviewUiState.Success(
            aircraft = aircraft,
            logStats = stats,
            inspectionCards = cardsWithStatus,
            showAddInspectionSheet = showSheet,
            selectedInspection = refreshedSelected,
            logsForSelectedInspection = refreshedDetailLogs,
            editingInspection = refreshedEditing,
            deletingInspectionId = current?.deletingInspectionId,
          )
        } else {
          AircraftOverviewUiState.Error
        }
      }.collect { state ->
        _uiState.update { state }
      }
    }
  }

  fun showAddInspectionSheet() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) state.copy(showAddInspectionSheet = true) else state
    }
  }

  fun hideAddInspectionSheet() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) state.copy(showAddInspectionSheet = false) else state
    }
  }

  fun showInspectionDetail(cardWithStatus: InspectionCardWithStatus) {
    val relevantLogs = cachedLogs
      .filter { cardWithStatus.card.id in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(
          selectedInspection = cardWithStatus,
          logsForSelectedInspection = relevantLogs,
        )
      } else state
    }
  }

  fun hideInspectionDetail() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(selectedInspection = null, logsForSelectedInspection = emptyList())
      } else state
    }
  }

  fun openEditInspection(cardWithStatus: InspectionCardWithStatus) {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(editingInspection = cardWithStatus, selectedInspection = null)
      } else state
    }
  }

  fun closeEditInspection() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(editingInspection = null)
      } else state
    }
  }

  fun saveEditedInspection(
    cardId: String,
    title: String,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    forceDueDate: com.squareup.wire.Instant?,
    forceDueTach: Float,
    notes: String = "",
  ) {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    viewModelScope.launch {
      val newCard = InspectionCard(
        id = cardId,
        title = title,
        component = component,
        rules = rules,
        force_due_tach = forceDueTach,
        force_due_date = forceDueDate,
        notes = notes,
      )
      inspectionManager.updateInspection(state.aircraft.id, newCard)
      closeEditInspection()
    }
  }

  fun requestDeleteInspection(cardId: String) {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(deletingInspectionId = cardId)
      } else state
    }
  }

  fun cancelDeleteInspection() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(deletingInspectionId = null)
      } else state
    }
  }

  fun confirmDeleteInspection() {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    val cardId = state.deletingInspectionId ?: return
    viewModelScope.launch {
      inspectionManager.deleteInspection(state.aircraft.id, cardId)
      _uiState.update { s ->
        if (s is AircraftOverviewUiState.Success) {
          s.copy(deletingInspectionId = null, editingInspection = null, selectedInspection = null)
        } else s
      }
    }
  }

  fun saveNewInspection(
    title: String,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    notes: String = "",
  ) {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    viewModelScope.launch {
      val card = InspectionCard(
        title = title,
        component = component,
        rules = rules,
        notes = notes,
      )
      inspectionManager.addInspection(state.aircraft.id, card)
      hideAddInspectionSheet()
    }
  }

  fun deleteAircraft() {
    viewModelScope.launch {
      aircraftManager.deleteAircraft(aircraftId)
        .onSuccess {
          _events.send(AircraftOverviewEvent.NavigateBack)
        }
        .onFailure { error ->
          _events.send(
            AircraftOverviewEvent.ShowError(
              error.message ?: "Failed to delete aircraft"
            )
          )
        }
    }
  }
}

sealed interface AircraftOverviewEvent {
  data object NavigateBack : AircraftOverviewEvent
  data class ShowError(val message: String) : AircraftOverviewEvent
}
