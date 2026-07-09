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
) : ViewModel() {

  private val _flags = MutableStateFlow(FeatureFlags())
  val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

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
