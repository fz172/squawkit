package dev.fanfly.wingslog.feature.notifications.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.notifications.datamanager.InstallIdStore
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The fields sign-in / token refresh write. Never touches `enabled`. */
@Serializable
private data class PushDeviceTokenWire(
  val token: String,
  val platform: String,
  // BaseTimestamp, not Timestamp: that is the type the ServerTimestamp sentinel is, and the
  // sentinel is what asks Firestore to stamp the doc rather than trusting a device clock. Same
  // shape as SyncDocWire.lastUpdateTimestamp.
  val updatedAt: BaseTimestamp = Timestamp.ServerTimestamp,
)

/** What [PushTokenRegistrar.setEnabled] writes. Never touches `token`/`platform`. */
@Serializable
private data class PushDeviceEnabledWire(
  val enabled: Boolean,
  val updatedAt: BaseTimestamp = Timestamp.ServerTimestamp,
)

/**
 * [PushTokenRegistrar], writing directly to `users/{uid}/push_devices/{installationId}` — plain
 * fields, never the entity sync path (design §7.1); rules already grant this via the own-tree rule,
 * so no `SyncWriter`/`EntityStore` involvement is needed or wanted.
 *
 * Self-driving on [FirebaseAuth.authStateChanged] combined with the last token this process has
 * seen — the same shape `SyncPreferences.state` uses — so "upsert on sign-in" and "upsert on token
 * refresh" collapse into one reactive pipeline rather than two call sites that have to agree.
 * Whichever arrives second re-fires the write, which is idempotent (`merge = true`).
 *
 * **Two separate partial writes, never one.** [onTokenRefreshed] writes `{token, platform}`;
 * [setEnabled] writes `{enabled}`. Neither ever touches the other's fields, so a token refresh can
 * never silently flip the per-device toggle back on, and toggling the switch can never stomp a
 * token that arrived a moment later. Both are `merge = true`,
 * so the first write for a brand-new device also never has to invent a value for the field it
 * doesn't own — an absent `enabled` reads as "on" server-side, which is correct for a fresh device.
 *
 * **Known gap, not closed here:** linking an anonymous account to a real one does not fire
 * `authStateChanged` (see `SettingsViewModel.refreshAccountState`'s comment for the same landmine
 * elsewhere) — so a token cached while signed in as a guest is never re-registered under the
 * upgraded uid, since nothing nudges this class after the upgrade. Guests never receive N1 in the
 * first place ([user.isAnonymous] gates registration below), so the practical effect is a one-token
 * delay: the *next* natural trigger (a token rotation, or the next cold start once the FCM receiver
 * proactively re-reads the current token) closes it. Worth a real fix if it proves to matter; not
 * exercised by anything today because sharing itself requires a real account.
 */
class PushTokenRegistrarImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
  private val installIdStore: InstallIdStore,
  /** `"android"` / `"ios"` / `"web"` — supplied by the platform's own Koin module. */
  private val platform: String,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PushTokenRegistrar {

  private val cachedToken = MutableStateFlow<String?>(null)

  init {
    scope.launch {
      combine(
        firebaseAuth.authStateChanged,
        cachedToken
      ) { user, token -> user to token }
        .collect { (user, token) ->
          // A guest has no account to fan collaboration into — sharing itself requires signing in
          // — so registering a token here would be pointless state with no consumer server-side.
          if (user == null || user.isAnonymous || token == null) return@collect
          try {
            upsertToken(user.uid, token)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            log.w(e) { "Could not register push token" }
          }
        }
    }
  }

  override suspend fun onTokenRefreshed(token: String) {
    cachedToken.value = token
  }

  override suspend fun setEnabled(enabled: Boolean) {
    val uid = firebaseAuth.currentUser?.uid ?: return
    try {
      deviceDoc(uid, installIdStore.getOrCreate())
        .set(PushDeviceEnabledWire(enabled = enabled), merge = true)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      log.w(e) { "Could not set this device's push enabled flag" }
    }
  }

  override suspend fun clearThisDevice() {
    val uid = firebaseAuth.currentUser?.uid ?: return
    // CancellationException is rethrown, not logged: the caller bounds this with a timeout, and
    // swallowing the timeout's cancellation would report a clean clear that never happened.
    try {
      deviceDoc(uid, installIdStore.getOrCreate()).delete()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      log.w(e) { "Could not clear this device's push token on sign-out" }
    }
  }

  private suspend fun upsertToken(uid: String, token: String) {
    deviceDoc(uid, installIdStore.getOrCreate()).set(
      PushDeviceTokenWire(token = token, platform = platform),
      merge = true,
    )
  }

  private fun deviceDoc(uid: String, installId: String): DocumentReference =
    firestore.collection("users")
      .document(uid)
      .collection("push_devices")
      .document(installId)

  private companion object {
    val log = Logger.withTag("PushTokenRegistrarImpl")
  }
}
