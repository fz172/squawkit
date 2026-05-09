package dev.fanfly.wingslog.feature.technician.manage.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditTechnicianUiState(
  val id: String = "",
  val name: String = "",
  val certType: CertificateType = CertificateType.CERTIFICATE_TYPE_NONE,
  val certNumber: String = "",
  val certExpireLimit: CertExpireLimit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
  val certExpiration: Instant? = null,
  val isSelf: Boolean = false,
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
  val saveSuccess: Boolean = false,
  val deleteSuccess: Boolean = false,
  val error: String? = null,
)

class EditTechnicianViewModel(
  private val technicianManager: TechnicianManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val technicianId: String? = savedStateHandle.get<String>(Screen.TECHNICIAN_ID)?.takeIf { it != "new" }

  private val _uiState = MutableStateFlow(EditTechnicianUiState(isLoading = technicianId != null))
  val uiState = _uiState.asStateFlow()

  init {
    if (technicianId != null) {
      loadTechnician(technicianId)
      observeSelfId(technicianId)
    }
  }

  private fun observeSelfId(id: String) {
    viewModelScope.launch {
      technicianManager.observeSelfId().collect { selfId ->
        _uiState.update { it.copy(isSelf = selfId == id) }
      }
    }
  }

  private fun loadTechnician(id: String) {
    viewModelScope.launch {
      val technician = technicianManager.loadTechnician(id).firstOrNull()
      if (technician != null) {
        _uiState.update {
          val certType = when {
            technician.certificate_type != CertificateType.CERTIFICATE_TYPE_NONE ->
              technician.certificate_type
            technician.cert_type.isBlank() || technician.cert_type == "NONE" ->
              CertificateType.CERTIFICATE_TYPE_NONE
            else -> try {
              CertificateType.valueOf(technician.cert_type)
            } catch (_: Exception) {
              CertificateType.CERTIFICATE_TYPE_NONE
            }
          }

          val expireLimit = when {
            technician.cert_expire_limit != CertExpireLimit.CERT_EXPIRE_LIMIT_UNKNOWN ->
              technician.cert_expire_limit
            technician.cert_expiration == null ->
              CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
            else ->
              CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES
          }

          it.copy(
            id = technician.id,
            name = technician.name,
            certType = certType,
            certNumber = technician.cert_number,
            certExpireLimit = expireLimit,
            certExpiration = technician.cert_expiration?.let { ts ->
              Instant.fromEpochSeconds(ts.getEpochSecond(), ts.getNano())
            },
            isLoading = false,
          )
        }
      } else {
        _uiState.update { it.copy(isLoading = false, error = "Failed to load technician") }
      }
    }
  }

  fun updateName(name: String) {
    _uiState.update { it.copy(name = name) }
  }

  fun updateCertType(certType: CertificateType) {
    _uiState.update {
      if (certType == CertificateType.CERTIFICATE_TYPE_NONE) {
        it.copy(
          certType = certType,
          certNumber = "",
          certExpireLimit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
          certExpiration = null,
        )
      } else {
        it.copy(certType = certType)
      }
    }
  }

  fun updateCertNumber(certNumber: String) {
    _uiState.update { it.copy(certNumber = certNumber) }
  }

  fun updateCertExpireLimit(expireLimit: CertExpireLimit) {
    _uiState.update { it.copy(certExpireLimit = expireLimit) }
  }

  fun updateCertExpiration(certExpiration: Instant?) {
    _uiState.update { it.copy(certExpiration = certExpiration) }
  }

  fun delete() {
    val id = _uiState.value.id
    if (id.isBlank()) return
    viewModelScope.launch {
      val result = technicianManager.deleteTechnician(id)
      if (result.isSuccess) {
        _uiState.update { it.copy(deleteSuccess = true) }
      } else {
        _uiState.update {
          it.copy(error = result.exceptionOrNull()?.message ?: "Failed to delete technician")
        }
      }
    }
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
        certificate_type = currentState.certType,
        cert_number = currentState.certNumber,
        cert_expire_limit = currentState.certExpireLimit,
        cert_expiration = if (currentState.certExpireLimit == CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES) {
          null
        } else {
          currentState.certExpiration?.let { toWireInstant(it.epochSeconds, it.nanosecondsOfSecond) }
        },
      )

      val result = technicianManager.updateTechnician(technician)
      if (result.isSuccess) {
        _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
      } else {
        _uiState.update {
          it.copy(isSaving = false, error = result.exceptionOrNull()?.message ?: "Failed to save technician")
        }
      }
    }
  }
}
