package dev.fanfly.wingslog.feature.maintenance.overview.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.wire.Instant
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.inspection.datamanager.InspectionManager
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus
import dev.fanfly.wingslog.feature.maintenance.database.AircraftManager
import dev.fanfly.wingslog.feature.maintenance.database.MaintenanceLogManager
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
  savedStateHandle: SavedStateHandle,
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
        inspectionManager.observeInspections(aircraftId),
        logManager.observeMaintenanceOverview(aircraftId)
      ) { aircraft, logs, inspectionCards, overview ->
        cachedLogs = logs
        if (aircraft != null) {
          val stats = if (overview != null) {
            LogStats(
              total = overview.total_log_count.toLong(),
              airframe = overview.airframe_log_count.toLong(),
              engine = overview.engine_log_count.toLong(),
              propeller = overview.propeller_log_count.toLong(),
              currentAirframeTime = overview.current_airframe_time,
              currentEngineTime = overview.current_engine_time,
              currentPropTime = overview.current_propeller_time
            )
          } else {
            // Fallback to manual compute if overview doesn't exist yet
            val currentEngineTime =
              logs.filter { it.engine_hour > 0.0 }.maxOfOrNull { it.engine_hour }
            val currentAirframeTime =
              logs.filter { it.airframe_time > 0.0 }.maxOfOrNull { it.airframe_time }
            val currentPropTime = logs.filter { it.prop_time > 0.0 }.maxOfOrNull { it.prop_time }
            LogStats(
              total = logs.size.toLong(),
              airframe = logs.count { it.component_type == MaintenanceLog.ComponentType.AIRFRAME }
                .toLong(),
              engine = logs.count { it.component_type == MaintenanceLog.ComponentType.ENGINE }
                .toLong(),
              propeller = logs.count { it.component_type == MaintenanceLog.ComponentType.PROPELLER }
                .toLong(),
              currentEngineTime = currentEngineTime,
              currentAirframeTime = currentAirframeTime,
              currentPropTime = currentPropTime
            )
          }

          // Compute due status for each inspection card
          val cardsWithStatus = inspectionCards.map { card ->
            InspectionCardWithStatus(
              card = card,
              dueStatus = inspectionManager.computeNextDue(card, logs, inspectionCards),
            )
          }
          val active = cardsWithStatus.filter { it.dueStatus.status != DueStatus.COMPLIED }
          val complied = cardsWithStatus.filter { it.dueStatus.status == DueStatus.COMPLIED }

          val current = _uiState.value as? AircraftOverviewUiState.Success
          // Refresh selected inspection due status if detail sheet is open
          val refreshedSelected = current?.selectedInspection?.let { sel ->
            cardsWithStatus.find { it.card.id == sel.card.id }
          }
          val refreshedDetailLogs = refreshedSelected?.let { sel ->
            logs.filter { sel.card.id in it.inspection_ids }
              .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
          } ?: emptyList()

          AircraftOverviewUiState.Success(
            aircraft = aircraft,
            logStats = stats,
            activeInspections = active,
            compliedInspections = complied,
            selectedInspection = refreshedSelected,
            logsForSelectedInspection = refreshedDetailLogs,
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

  fun saveEditedInspection(
    cardId: String,
    title: String,
    type: ComplianceType,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    referenceNumber: String,
    complianceAuthority: String,
    complianceDetails: String,
    isOneTime: Boolean,
    forceDueDate: Instant?,
    forceDueEngine: Float,
    notes: String,
  ) {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    viewModelScope.launch {
      val updatedCard = InspectionCard(
        id = cardId,
        title = title,
        type = type,
        component = component,
        rules = rules,
        reference_number = referenceNumber,
        compliance_authority = complianceAuthority,
        compliance_details = complianceDetails,
        is_one_time = isOneTime,
        force_due_date = forceDueDate,
        force_due_engine_hour = forceDueEngine,
        notes = notes,
      )
      inspectionManager.updateInspection(state.aircraft.id, updatedCard)
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
    deleteInspection(cardId)
  }

  fun deleteInspection(cardId: String) {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    viewModelScope.launch {
      inspectionManager.deleteInspection(state.aircraft.id, cardId)
      _uiState.update { s ->
        if (s is AircraftOverviewUiState.Success) {
          s.copy(deletingInspectionId = null, selectedInspection = null)
        } else s
      }
    }
  }

  fun saveNewInspection(
    title: String,
    type: ComplianceType,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    referenceNumber: String,
    complianceAuthority: String,
    complianceDetails: String,
    isOneTime: Boolean,
    forceDueDate: Instant?,
    forceDueEngine: Float,
    notes: String = "",
  ) {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    viewModelScope.launch {
      val card = InspectionCard(
        title = title,
        type = type,
        component = component,
        rules = rules,
        reference_number = referenceNumber,
        compliance_authority = complianceAuthority,
        compliance_details = complianceDetails,
        is_one_time = isOneTime,
        force_due_date = forceDueDate,
        force_due_engine_hour = forceDueEngine,
        notes = notes,
      )
      inspectionManager.addInspection(state.aircraft.id, card)
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
              error.message
            )
          )
        }
    }
  }
}

sealed interface AircraftOverviewEvent {
  data object NavigateBack : AircraftOverviewEvent
  data class ShowError(val message: String?) : AircraftOverviewEvent
}
