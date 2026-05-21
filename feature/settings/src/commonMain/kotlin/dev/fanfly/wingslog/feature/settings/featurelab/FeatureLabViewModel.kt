package dev.fanfly.wingslog.feature.settings.featurelab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeatureLabViewModel(
  private val featureLabManager: FeatureLabManager,
  private val backendProbe: FeatureLabBackendProbe,
) : ViewModel() {

  private val _flags = MutableStateFlow(FeatureFlags())
  val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()
  private val _backendStatus =
    MutableStateFlow("Ready to call the local emulator.")
  val backendStatus: StateFlow<String> = _backendStatus.asStateFlow()
  private val _backendRunning = MutableStateFlow(false)
  val backendRunning: StateFlow<Boolean> = _backendRunning.asStateFlow()

  init {
    viewModelScope.launch {
      featureLabManager.observe().collect { _flags.value = it }
    }
  }

  fun setTechnicianEnabled(enabled: Boolean) {
    viewModelScope.launch {
      featureLabManager.update(_flags.value.copy(technicianEnabled = enabled))
    }
  }

  fun setAttachmentUploadEnabled(enabled: Boolean) {
    viewModelScope.launch {
      featureLabManager.update(_flags.value.copy(attachmentUploadEnabled = enabled))
    }
  }

  fun setExportLogsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      featureLabManager.update(_flags.value.copy(exportLogsEnabled = enabled))
    }
  }

  fun runBackendProbe() {
    if (_backendRunning.value) return
    viewModelScope.launch {
      _backendRunning.value = true
      _backendStatus.value = "Calling local emulator..."
      _backendStatus.value = runCatching { backendProbe.callHealthProbe() }
        .getOrElse { throwable -> "Call failed: ${throwable.message ?: throwable::class.simpleName}" }
      _backendRunning.value = false
    }
  }
}
