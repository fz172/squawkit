package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeveloperOptionsViewModel(
  private val featureLabManager: DeveloperOptionsManager,
) : ViewModel() {

  private val _flags = MutableStateFlow(DeveloperFlags())
  val flags: StateFlow<DeveloperFlags> = _flags.asStateFlow()

  init {
    viewModelScope.launch {
      featureLabManager.observe()
        .collect { _flags.value = it }
    }
  }

  fun setAttachmentUploadEnabled(enabled: Boolean) {
    viewModelScope.launch {
      featureLabManager.update(_flags.value.copy(attachmentUploadEnabled = enabled))
    }
  }

  fun setExportEmailDeliveryEnabled(enabled: Boolean) {
    viewModelScope.launch {
      featureLabManager.update(_flags.value.copy(exportEmailDeliveryEnabled = enabled))
    }
  }
}
