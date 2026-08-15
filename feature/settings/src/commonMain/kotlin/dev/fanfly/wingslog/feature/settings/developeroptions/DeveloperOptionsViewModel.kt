package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

  /** Show display ads regardless of tier, so placement can be exercised on any account. */
  fun setForceAds(enabled: Boolean) {
    viewModelScope.launch {
      developerOptionsManager.update(_flags.value.copy(forceAds = enabled))
    }
  }

  private var testDeviceHashPersistJob: Job? = null

  /**
   * Registers this device with Google UMP so the EEA debug-geography override takes effect.
   *
   * Updates [_flags] optimistically and immediately, rather than only through [developerOptionsManager]'s
   * round trip: this is free text typed one keystroke at a time, and the text field's value is bound
   * straight to [flags]. Waiting for `observe()` to echo each keystroke back meant the field showed
   * the *previous* value again on the very next recomposition — before the write had round-tripped —
   * which erased every character as it was typed. The write itself is debounced so a fast typist
   * doesn't fire one persistence call per keystroke; only the value typing settles on is persisted.
   */
  fun setAdConsentTestDeviceHashedId(hashedId: String) {
    val normalized = hashedId.ifBlank { null }
    _flags.value = _flags.value.copy(adConsentTestDeviceHashedId = normalized)

    testDeviceHashPersistJob?.cancel()
    testDeviceHashPersistJob = viewModelScope.launch {
      delay(500.milliseconds)
      developerOptionsManager.update(_flags.value)
    }
  }

}
