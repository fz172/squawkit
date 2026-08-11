package dev.fanfly.wingslog.feature.login.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.auth.upgradeProvidersFor
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
  private val emailStore: UpgradeEmailStore,
  private val appCapability: AppCapability,
) : ViewModel() {

  private val _state = MutableStateFlow<UpgradeUiState>(UpgradeUiState.Idle)
  val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

  /**
   * What a confirmed merge needs, parked while the user reads the explanation.
   *
   * Held here rather than in [UpgradeUiState] so an `AuthCredential` never reaches the UI layer.
   * [credential] is null when the provider's one was single-use and has been spent — Apple — in
   * which case confirming re-authorizes instead of replaying it.
   */
  private data class PendingMerge(
    val provider: AuthProvider,
    val guestUid: String,
    val guestName: String?,
    val credential: AuthCredential?,
  )

  private var pendingMerge: PendingMerge? = null

  /** Opens the picker. What it contains is derived from platform capability, not hardcoded. */
  fun choose() {
    if (_state.value == UpgradeUiState.Working) return
    _state.value = UpgradeUiState.ChoosingProvider(
      upgradeProvidersFor(appCapability.isAppleSignInSupported)
    )
  }

  /** Picker selection. Email needs an address first; the other two can start immediately. */
  fun select(provider: AuthProvider) {
    when (provider) {
      AuthProvider.Email -> _state.value = UpgradeUiState.EnteringEmail()
      AuthProvider.Google, AuthProvider.Apple -> startUpgrade(provider)
    }
  }

  /** Email field edits live in state so they survive a keyboard or rotation teardown. */
  fun setEmail(value: String) {
    val current = _state.value
    if (current !is UpgradeUiState.EnteringEmail || current.sending) return
    _state.value = current.copy(email = value, error = null)
  }

  /** Leg 1 — send the link, and remember the address for the confirmation step. */
  fun sendEmailLink() {
    val current = _state.value
    if (current !is UpgradeUiState.EnteringEmail || current.sending) return
    val guestUid = authManager.getCurrentUser()?.uid
      ?: run {
        _state.value = UpgradeUiState.Error("No signed-in user to upgrade")
        return
      }

    _state.value = current.copy(sending = true, error = null)
    viewModelScope.launch {
      _state.value =
        when (val result = authManager.sendSignInLink(current.email)) {
          is SendLinkResult.Sent -> {
            emailStore.savePendingEmail(guestUid, result.email)
            UpgradeUiState.LinkSent(result.email)
          }

          is SendLinkResult.InvalidEmail ->
            current.copy(sending = false, error = "Enter a valid email address")

          is SendLinkResult.Failed ->
            current.copy(sending = false, error = result.message)
        }
    }
  }

  /**
   * Offers an inbound email link to the upgrade flow. When it belongs to *this* guest's pending
   * upgrade, the flow claims it and asks for confirmation; otherwise it is left untouched.
   *
   * Driven by the screen rather than collected here, mirroring how `AuthFlow` observes the same
   * channel: `EmailLinkDeepLinks` is a process-wide singleton, so a ViewModel that subscribed on
   * its own would keep consuming links for as long as its scope outlived the screen.
   *
   * Not claiming an unrecognised link is what keeps the two consumers from fighting: `AuthFlow`
   * only runs when nobody is signed in, and a link issued for a different session must reach it
   * rather than being applied to this device's data.
   */
  fun onIncomingLink(link: String) {
    if (_state.value is UpgradeUiState.ConfirmLink) return
    val user = authManager.getCurrentUser()
    if (user == null) {
      logger.d { "Ignoring inbound link: nobody signed in" }
      return
    }
    if (!user.isAnonymous) {
      logger.d { "Ignoring inbound link: current user is not a guest" }
      return
    }
    // Never log the link itself — it carries the oobCode, which is a usable credential.
    if (!authManager.isSignInWithEmailLink(link)) {
      logger.d { "Ignoring inbound link: not a Firebase sign-in link" }
      return
    }

    viewModelScope.launch {
      val pending = emailStore.pendingEmail(user.uid)
      if (pending == null) {
        logger.d { "Ignoring inbound link: no upgrade pending for this guest session" }
        return@launch
      }
      EmailLinkDeepLinks.consume()
      _state.value = UpgradeUiState.ConfirmLink(email = pending, link = link)
    }
  }

  /** Leg 2, once the user has confirmed. Links rather than signs in, preserving the guest UID. */
  fun confirmEmailLink() {
    val current = _state.value
    if (current !is UpgradeUiState.ConfirmLink) return
    val guestUid = authManager.getCurrentUser()?.uid
      ?: run {
        _state.value = UpgradeUiState.Error("No signed-in user to upgrade")
        return
      }

    _state.value = UpgradeUiState.Working
    viewModelScope.launch {
      val guestName = currentSelfName()
      val result =
        authManager.completeUpgradeWithEmailLink(current.email, current.link)
      _state.value = when (result) {
        is AccountUpgradeResult.Linked -> {
          emailStore.clear(guestUid)
          finishLinkedAccount(result.user.uid)
        }

        is AccountUpgradeResult.CredentialInUse -> {
          emailStore.clear(guestUid)
          askToMerge(AuthProvider.Email, guestUid, guestName, result.credential)
        }

        is AccountUpgradeResult.ReauthRequiredToMerge -> {
          emailStore.clear(guestUid)
          askToMerge(result.provider, guestUid, guestName, credential = null)
        }

        is AccountUpgradeResult.Cancelled -> UpgradeUiState.Idle
        is AccountUpgradeResult.Failed -> failed(result.message)
      }
    }
  }

  /**
   * Backs out of the flow.
   *
   * Closing [UpgradeUiState.LinkSent] must **not** discard the stashed address: that dialog exists
   * to tell the user to go and open the link, so leaving it is the expected next step, not a
   * change of mind. Clearing there deleted the pending upgrade before the link could be used, and
   * the returning link was then ignored for having nothing pending.
   *
   * Every other state is a real back-out — including [UpgradeUiState.ConfirmLink], where cancelling
   * means "that isn't my address", so the stash should go.
   */
  fun cancel() {
    val current = _state.value
    pendingMerge = null
    _state.value = UpgradeUiState.Idle
    if (current is UpgradeUiState.LinkSent) return

    val guestUid = authManager.getCurrentUser()?.uid ?: return
    viewModelScope.launch { emailStore.clear(guestUid) }
  }

  fun startUpgrade(provider: AuthProvider) {
    if (_state.value == UpgradeUiState.Working) return
    // Capture the guest UID before provider sign-in can switch FirebaseAuth to an existing user.
    val guestUid = authManager.getCurrentUser()?.uid
    _state.value = UpgradeUiState.Working
    viewModelScope.launch {
      // The name the user typed on the welcome screen is their self-technician's name; read it while
      // the guest is still the current user (before sign-in re-points the store) so the merge path
      // can carry it onto the account.
      val guestName = currentSelfName()
      _state.value =
        when (val result = authManager.upgradeAnonymousAccount(provider)) {
          is AccountUpgradeResult.Linked -> finishLinkedAccount(result.user.uid)
          is AccountUpgradeResult.CredentialInUse ->
            if (guestUid == null) {
              UpgradeUiState.Error("No signed-in user to merge")
            } else {
              askToMerge(provider, guestUid, guestName, result.credential)
            }

          is AccountUpgradeResult.ReauthRequiredToMerge ->
            if (guestUid == null) {
              UpgradeUiState.Error("No signed-in user to merge")
            } else {
              askToMerge(result.provider, guestUid, guestName, credential = null)
            }

          is AccountUpgradeResult.Cancelled -> UpgradeUiState.Idle
          is AccountUpgradeResult.Failed -> failed(result.message)
        }
    }
  }

  /** The current user's self-technician name, or null if unset. Blank is treated as unset. */
  private suspend fun currentSelfName(): String? =
    technicianManager.observeSelf()
      .firstOrNull()
      ?.name
      ?.takeIf { it.isNotBlank() }

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
    val name = currentSelfName() ?: return
    authManager.updateDisplayName(name)
  }

  /**
   * Parks the merge and asks first.
   *
   * The user chose "upgrade", not "sign in to that other account" — and merging re-keys this
   * device's records into an account that already has its own. Saying so before it happens also
   * gives the second Apple sheet a reason to exist, instead of looking like the first one failed.
   */
  private fun askToMerge(
    provider: AuthProvider,
    guestUid: String,
    guestName: String?,
    credential: AuthCredential?,
  ): UpgradeUiState {
    pendingMerge = PendingMerge(provider, guestUid, guestName, credential)
    return UpgradeUiState.ConfirmMerge(
      provider = provider,
      needsReauthorization = credential == null,
    )
  }

  /** The user accepted the merge. Only now does a second provider sheet appear, if one is needed. */
  fun confirmMerge() {
    val pending = pendingMerge ?: return
    if (_state.value !is UpgradeUiState.ConfirmMerge) return

    _state.value = UpgradeUiState.Working
    viewModelScope.launch {
      val result = if (pending.credential != null) {
        authManager.signInToExistingAccount(pending.credential)
      } else {
        authManager.mergeIntoExistingAccount(pending.provider)
      }
      _state.value = finishMerge(pending.guestUid, pending.guestName, result)
      pendingMerge = null
    }
  }

  private suspend fun finishMerge(
    guestUid: String,
    guestName: String?,
    result: AccountUpgradeResult,
  ): UpgradeUiState {
    return when (result) {
      is AccountUpgradeResult.Linked -> {
        val accountUid = result.user.uid
        if (!awaitPermanentCurrentUser(accountUid)) {
          UpgradeUiState.Error("Sign-in did not switch to the permanent account")
        } else {
          // Re-key this device's records into the existing account; the sync engine pushes them up.
          migrator.reassign(fromUid = guestUid, toUid = accountUid)
          // The merge keeps the account's own identity (its self-technician, roster links, toggles),
          // but the name the user chose on the welcome screen follows them across: ensureSelfProfile
          // settles the account's self-technician, then we stamp the guest name onto it. Skipped when
          // the guest never set one, so the account keeps its existing name.
          technicianManager.ensureSelfProfile()
          if (guestName != null) technicianManager.saveSelfName(guestName)
          pushSelfNameToAuthProfile()
          // Sign-in fired authStateChanged, but re-keying happened after; hydrate and nudge sync
          // so local reads include the permanent account's aircraft before the UI leaves Working.
          refreshLocalAccountData()
          UpgradeUiState.Success
        }
      }

      is AccountUpgradeResult.Failed -> failed(result.message)
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

  /**
   * The UI shows generic copy for failures, so the provider's own message would otherwise be lost
   * — and it is the only thing that says *why*. Logged at warn so a failure is diagnosable from a
   * normal log capture rather than a stack trace.
   */
  private fun failed(message: String): UpgradeUiState.Error {
    logger.w { "Account upgrade failed: $message" }
    return UpgradeUiState.Error(message)
  }

  companion object {
    private val logger = Logger.withTag("AccountUpgradeViewModel")
    private const val CURRENT_USER_SWITCH_TIMEOUT_MS = 3_000L
    private const val CURRENT_USER_SWITCH_POLL_MS = 50L
  }
}
