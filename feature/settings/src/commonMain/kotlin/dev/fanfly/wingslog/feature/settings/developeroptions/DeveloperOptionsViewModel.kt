package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeveloperOptionsViewModel(
  private val developerOptionsManager: DeveloperOptionsManager,
) : ViewModel() {

  private val _flags = MutableStateFlow(DeveloperFlags())
  val flags: StateFlow<DeveloperFlags> = _flags.asStateFlow()

  init {
    viewModelScope.launch {
      developerOptionsManager.observe()
        .collect { _flags.value = it }
    }
  }

  /** `null` clears the override (use the real entitlement); FREE/PRO force that tier locally. */
  fun setForceSubscriptionStatus(status: Subscription.Status?) {
    viewModelScope.launch {
      developerOptionsManager.update(_flags.value.copy(forceSubscriptionStatus = status))
    }
  }
}
