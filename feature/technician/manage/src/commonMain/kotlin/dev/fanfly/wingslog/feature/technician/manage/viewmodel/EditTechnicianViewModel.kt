package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.datetime.createWireInstant
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class EditTechnicianUiState(
  val id: String = "",
  val name: String = "",
  val certType: String = "",
  val certNumber: String = "",
  val certExpiration: Instant? = null,
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
  val saveSuccess: Boolean = false,
  val error: String? = null
)

class EditTechnicianViewModel(
  private val technicianManager: TechnicianManager,
  private val technicianId: String? = null
) : ViewModel() {

  private val _uiState = MutableStateFlow(EditTechnicianUiState(isLoading = technicianId != null))
  val uiState = _uiState.asStateFlow()

  init {
    if (technicianId != null) {
      loadTechnician(technicianId)
    }
  }

  private fun loadTechnician(id: String) {
    viewModelScope.launch {
      val technician = technicianManager.loadTechnician(id).firstOrNull()
      if (technician != null) {
        _uiState.update {
          it.copy(
            id = technician.id,
            name = technician.name,
            certType = technician.cert_type,
            certNumber = technician.cert_number,
            certExpiration = technician.cert_expiration?.let { ts ->
              Instant.fromEpochSeconds(ts.getEpochSecond(), ts.getNano())
            },
            isLoading = false
          )
        }
      } else {
        _uiState.update {
          it.copy(
            isLoading = false,
            error = "Failed to load technician"
          )
        }
      }
    }
  }

  fun updateName(name: String) {
    _uiState.update { it.copy(name = name) }
  }

  fun updateCertType(certType: String) {
    _uiState.update { it.copy(certType = certType) }
  }

  fun updateCertNumber(certNumber: String) {
    _uiState.update { it.copy(certNumber = certNumber) }
  }

  fun updateCertExpiration(certExpiration: Instant?) {
    _uiState.update { it.copy(certExpiration = certExpiration) }
  }

  fun save() {
    val currentState = _uiState.value
    if (currentState.name.isBlank()) {
      _uiState.update { it.copy(error = "Name is required") }
      return
    }

    _uiState.update { it.copy(isSaving = true, error = null) }

    viewModelScope.launch {
      val technician = Technician(
        id = currentState.id,
        name = currentState.name,
        cert_type = currentState.certType,
        cert_number = currentState.certNumber,
        cert_expiration = currentState.certExpiration?.let {
          createWireInstant(it.epochSeconds, it.nanosecondsOfSecond)
        }
      )

      val result = technicianManager.updateTechnician(technician)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
      } else {
        _uiState.update {
          it.copy(
            isSaving = false,
            error = result.exceptionOrNull()?.message ?: "Failed to save technician"
          )
        }
      }
    }
  }
}
