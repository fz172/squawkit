package dev.fanfly.wingslog.feature.settings.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.LocalAccountMigrator
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.AuthCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

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
  private val technicianManager: TechnicianManager,
  private val syncEngine: SyncEngine,
) : ViewModel() {

  private val _state = MutableStateFlow<UpgradeUiState>(UpgradeUiState.Idle)
  val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

  fun startUpgrade() {
    if (_state.value == UpgradeUiState.Working) return
    // Capture the guest UID before provider sign-in can switch FirebaseAuth to an existing user.
    val guestUid = authManager.getCurrentUser()?.uid
    _state.value = UpgradeUiState.Working
    viewModelScope.launch {
      _state.value = when (val result = authManager.upgradeAnonymousAccount()) {
        is AccountUpgradeResult.Linked -> finishLinkedAccount(result.user.uid)
        is AccountUpgradeResult.CredentialInUse -> {
          if (guestUid == null) {
            UpgradeUiState.Error("No signed-in user to merge")
          } else {
            mergeExistingAccount(
              guestUid = guestUid,
              credential = result.credential
            )
          }
        }

        is AccountUpgradeResult.Cancelled -> UpgradeUiState.Idle
        is AccountUpgradeResult.Failed -> UpgradeUiState.Error(result.message)
      }
    }
  }

  private suspend fun finishLinkedAccount(accountUid: String): UpgradeUiState {
    if (!awaitPermanentCurrentUser(accountUid)) {
      return UpgradeUiState.Error("Sign-in did not switch to the permanent account")
    }

    // Linking doesn't fire authStateChanged, so seed the profile (name + photo) and kick the sync
    // engine explicitly — otherwise the now-permanent data never reaches the cloud.
    //
    // The guest's name SURVIVES the upgrade: it is the name the user chose, and being handed a
    // Google account is not a reason to be renamed. ensureSelfProfile only fills a blank profile.
    technicianManager.ensureSelfProfile()
    pushSelfNameToAuthProfile()
    refreshLocalAccountData()
    return UpgradeUiState.Success
  }

  /**
   * Mirrors the in-app name onto the Firebase Auth profile so the ID token carries it.
   *
   * Cloud Functions cannot read the self-technician record — they see only the token. The invite's
   * `hostName` is stamped from `token.name`, so without this the person you invite would be shown
   * your Google name while everyone else in the app sees the name you chose.
   */
  private suspend fun pushSelfNameToAuthProfile() {
    val name = technicianManager.observeSelf()
      .firstOrNull()
      ?.name
      ?.takeIf { it.isNotBlank() }
      ?: return
    authManager.updateDisplayName(name)
  }

  private suspend fun mergeExistingAccount(
    guestUid: String,
    credential: AuthCredential,
  ): UpgradeUiState {
    return when (val result = authManager.signInToExistingAccount(credential)) {
      is AccountUpgradeResult.Linked -> {
        val accountUid = result.user.uid
        if (!awaitPermanentCurrentUser(accountUid)) {
          UpgradeUiState.Error("Sign-in did not switch to the permanent account")
        } else {
          // Re-key this device's records into the existing account; the sync engine pushes them up.
          migrator.reassign(fromUid = guestUid, toUid = accountUid)
          // Whatever self-profile the account already has wins here — this is a merge INTO an
          // existing identity, not a promotion of the guest one. A blank one still gets seeded.
          technicianManager.ensureSelfProfile()
          pushSelfNameToAuthProfile()
          // Sign-in fired authStateChanged, but re-keying happened after; hydrate and nudge sync
          // so local reads include the permanent account's aircraft before the UI leaves Working.
          refreshLocalAccountData()
          UpgradeUiState.Success
        }
      }

      is AccountUpgradeResult.Failed -> UpgradeUiState.Error(result.message)
      else -> UpgradeUiState.Error("Sign-in failed")
    }
  }

  /** Dismiss a terminal (Success/Error) state back to Idle. */
  fun dismiss() {
    _state.value = UpgradeUiState.Idle
  }

  private suspend fun awaitPermanentCurrentUser(expectedUid: String): Boolean =
    withTimeoutOrNull(CURRENT_USER_SWITCH_TIMEOUT_MS.milliseconds) {
      while (true) {
        val current = authManager.getCurrentUser()
        if (current?.uid == expectedUid && !current.isAnonymous) return@withTimeoutOrNull true
        delay(CURRENT_USER_SWITCH_POLL_MS.milliseconds)
      }
    } == true

  private suspend fun refreshLocalAccountData() {
    syncEngine.hydrateCurrentUserNow()
    syncEngine.resyncCurrentUser()
  }

  companion object {
    private const val CURRENT_USER_SWITCH_TIMEOUT_MS = 3_000L
    private const val CURRENT_USER_SWITCH_POLL_MS = 50L
  }
}
