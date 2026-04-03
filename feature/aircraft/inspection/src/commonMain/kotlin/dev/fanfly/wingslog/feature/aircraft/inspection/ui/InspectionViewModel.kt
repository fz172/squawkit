package dev.fanfly.wingslog.feature.aircraft.inspection.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.wire.Instant
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.feature.aircraft.inspection.database.InspectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InspectionUiState {
  data object Loading : InspectionUiState
  data class Success(
    val aircraftId: String,
    val allInspections: List<InspectionCard> = emptyList(),
  ) : InspectionUiState
}

class InspectionViewModel(
  private val inspectionManager: InspectionManager,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val aircraftId: String = checkNotNull(savedStateHandle["aircraftId"])
  val cardId: String? = savedStateHandle["cardId"]

  private val _uiState = MutableStateFlow<InspectionUiState>(InspectionUiState.Loading)
  val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

  init {
    loadData()
  }

  private fun loadData() {
    viewModelScope.launch {
      inspectionManager.observeInspections(aircraftId).collect { cards ->
        _uiState.update { InspectionUiState.Success(aircraftId, cards) }
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
    onSuccess: () -> Unit
  ) {
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
      inspectionManager.addInspection(aircraftId, card)
        .onSuccess { onSuccess() }
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
    onSuccess: () -> Unit
  ) {
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
      inspectionManager.updateInspection(aircraftId, updatedCard)
        .onSuccess { onSuccess() }
    }
  }

  fun deleteInspection(cardId: String, onSuccess: () -> Unit) {
    viewModelScope.launch {
      inspectionManager.deleteInspection(aircraftId, cardId)
        .onSuccess { onSuccess() }
    }
  }
}
