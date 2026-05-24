package dev.fanfly.wingslog.feature.settings.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.LocalAccountMigrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates upgrading a guest (anonymous) session to a permanent account.
 *
 * Clean path: [AuthManager.upgradeAnonymousAccount] links the provider to the anonymous user
 * (UID preserved), so local data needs no migration and the sync engine pushes it up on its own.
 *
 * Merge path: when the chosen account already exists, [AuthManager.signInToExistingAccount] signs
 * in to it (new UID) and [LocalAccountMigrator.reassign] re-keys this device's records into it.
 */
class AccountUpgradeViewModel(
  private val authManager: AuthManager,
  private val migrator: LocalAccountMigrator,
) : ViewModel() {

  private val log = Logger.withTag(TAG)

  private val _state = MutableStateFlow<UpgradeUiState>(UpgradeUiState.Idle)
  val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

  fun startUpgrade() {
    if (_state.value == UpgradeUiState.Working) return
    _state.value = UpgradeUiState.Working
    viewModelScope.launch {
      _state.value = when (val result = authManager.upgradeAnonymousAccount()) {
        is AccountUpgradeResult.Linked -> UpgradeUiState.Success
        is AccountUpgradeResult.CredentialInUse -> UpgradeUiState.ConfirmMerge(result.credential)
        is AccountUpgradeResult.Cancelled -> UpgradeUiState.Idle
        is AccountUpgradeResult.Failed -> UpgradeUiState.Error(result.message)
      }
    }
  }

  fun confirmMerge() {
    val confirm = _state.value as? UpgradeUiState.ConfirmMerge ?: return
    // Capture the guest UID before switching accounts; we're still anonymous at this point.
    val guestUid = authManager.getCurrentUser()?.uid
    if (guestUid == null) {
      _state.value = UpgradeUiState.Error("No signed-in user to merge")
      return
    }
    _state.value = UpgradeUiState.Working
    viewModelScope.launch {
      _state.value = when (val result = authManager.signInToExistingAccount(confirm.credential)) {
        is AccountUpgradeResult.Linked -> {
          // Re-key this device's records into the existing account; the sync engine pushes them up.
          migrator.reassign(fromUid = guestUid, toUid = result.user.uid)
          UpgradeUiState.Success
        }

        is AccountUpgradeResult.Failed -> UpgradeUiState.Error(result.message)
        else -> {
          log.w { "Unexpected result on merge sign-in: $result" }
          UpgradeUiState.Error("Sign-in failed")
        }
      }
    }
  }

  /** User declined the merge; stay a guest with data intact. */
  fun cancelMerge() {
    _state.value = UpgradeUiState.Idle
  }

  /** Dismiss a terminal (Success/Error) state back to Idle. */
  fun dismiss() {
    _state.value = UpgradeUiState.Idle
  }

  companion object {
    private const val TAG = "AccountUpgradeViewModel"
  }
}
