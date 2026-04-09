package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.datetime.createWireInstant
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
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
  val certType: LicenseType = LicenseType.NONE,
  val certNumber: String = "",
  val certExpireLimit: LicenseExpireLimit = LicenseExpireLimit.EXPIRES,
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
          val type = try {
            if (technician.cert_type.isBlank() || technician.cert_type == "NONE") LicenseType.NONE else LicenseType.valueOf(technician.cert_type)
          } catch (e: Exception) {
            LicenseType.NONE
          }
          val expireLimit = if (technician.cert_expiration == null) LicenseExpireLimit.NEVER_EXPIRES else LicenseExpireLimit.EXPIRES

          it.copy(
            id = technician.id,
            name = technician.name,
            certType = type,
            certNumber = technician.cert_number,
            certExpireLimit = expireLimit,
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

  fun updateCertType(certType: LicenseType) {
    _uiState.update { it.copy(certType = certType) }
  }

  fun updateCertNumber(certNumber: String) {
    _uiState.update { it.copy(certNumber = certNumber) }
  }
  
  fun updateCertExpireLimit(expireLimit: LicenseExpireLimit) {
    _uiState.update { it.copy(certExpireLimit = expireLimit) }
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
        cert_type = if (currentState.certType == LicenseType.NONE) "" else currentState.certType.name,
        cert_number = currentState.certNumber,
        cert_expiration = if (currentState.certExpireLimit == LicenseExpireLimit.NEVER_EXPIRES) null else currentState.certExpiration?.let {
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
