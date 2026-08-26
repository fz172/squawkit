package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeveloperOptionsViewModel(
  private val developerOptionsManager: DeveloperOptionsManager,
  private val adConsentManager: AdConsentManager,
) : ViewModel() {

  private val _flags = MutableStateFlow(DeveloperFlags())
  val flags: StateFlow<DeveloperFlags> = _flags.asStateFlow()

  /** The most recently typed/toggled value not yet claimed by [startPersistWorker]'s loop. */
  private val pendingWrite = MutableStateFlow<DeveloperFlags?>(null)

  /**
   * True from the moment a write is queued until the worker has drained everything queued and
   * confirmed it — see [startPersistWorker]. Gates the `observe()` collector below: while true, at
   * least one local edit hasn't round-tripped yet, so an echo — which may reflect an *earlier* write
   * that happens to finish after a later one — must not be allowed to overwrite `_flags`.
   */
  private var persistWorkerRunning = false

  init {
    viewModelScope.launch {
      developerOptionsManager.observe()
        .collect { persisted ->
          if (!persistWorkerRunning) _flags.value = persisted
        }
    }
  }

  /** `null` clears the override (use the real entitlement); FREE/PRO force that tier locally. */
  fun setForceSubscriptionStatus(status: Subscription.Status?) {
    setFlags(_flags.value.copy(forceSubscriptionStatus = status))
  }

  /** Show display ads regardless of tier, so placement can be exercised on any account. */
  fun setForceAds(enabled: Boolean) {
    setFlags(_flags.value.copy(forceAds = enabled))
  }

  /** Registers this device with Google UMP so the EEA debug-geography override takes effect. */
  fun setAdConsentTestDeviceHashedId(hashedId: String) {
    setFlags(_flags.value.copy(adConsentTestDeviceHashedId = hashedId.ifBlank { null }))
  }

  /**
   * Wipes the on-device UMP cache so consent resolves fresh next time, as if this were a first-ever
   * launch — the only way to re-test the onboarding priming explainer (`AuthFlow`) without clearing
   * the app's local data/account entirely, since neither platform's SDK re-asks once already
   * resolved. Takes effect on the *next* app launch: `AuthFlow`'s consent check only runs from its
   * own step machine, which only mounts at a fresh cold start, not from within Developer Options.
   */
  fun resetAdConsent() {
    viewModelScope.launch { adConsentManager.resetConsent() }
  }

  /**
   * Applies [flags] to the UI immediately and queues it for persistence.
   *
   * The UI update is synchronous and never waits on [developerOptionsManager] — [flags] is bound
   * straight to a text field for the test-device hash, typed one keystroke at a time, and a field
   * whose value only updates once a write round-trips back through `observe()` visibly erases
   * whatever was just typed the moment the next keystroke recomposes before that round trip lands.
   *
   * Persistence itself goes through [startPersistWorker] rather than a fire-and-forget `launch` per
   * call:
   * plain concurrent launches can complete out of order, and whichever's `observe()` echo lands last
   * — not necessarily the one for the latest edit — would win and silently revert to older text. The
   * worker keeps exactly one write in flight and always sends the newest pending value next, so
   * typing is never lost regardless of how slow the device or the round trip is; no arbitrary delay
   * involved; a fast typist just coalesces onto fewer writes automatically, not by a guessed timeout.
   */
  private fun setFlags(flags: DeveloperFlags) {
    _flags.value = flags
    pendingWrite.value = flags
    if (!persistWorkerRunning) startPersistWorker()
  }

  private fun startPersistWorker() {
    persistWorkerRunning = true
    viewModelScope.launch {
      while (true) {
        val next = pendingWrite.value ?: break
        // Claimed: a newer edit arriving while `update` below is in flight sets pendingWrite again,
        // and the loop picks it up next — never concurrently with this call.
        pendingWrite.value = null
        developerOptionsManager.update(next)
      }
      persistWorkerRunning = false
    }
  }

}
