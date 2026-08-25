package dev.fanfly.wingslog.feature.notifications.datamanager

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessaging
import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Feeds the token this device *already has* into [PushTokenSink] at startup.
 *
 * **`onNewToken` is an edge, not a state.** FCM fires it once when a token is first minted for an
 * install and then only on rotation, so a pipeline armed solely by that callback registers nothing
 * on any device whose token predates the code — which is every device that had the app installed
 * before push shipped. The first end-to-end test of the fan-out logged `recipients: 2, sent: 0` for
 * exactly this reason: the server resolved the audience correctly and had no token to send to.
 *
 * It also breaks a circular lazy-init. [PushTokenRegistrar] is a lazy `single` whose only injection
 * point was `WingsLogFirebaseMessagingService`, and that service is only constructed when FCM
 * delivers something — which cannot happen until a token is registered. Constructing this at Koin
 * start pulls the registrar up with it, so its `authStateChanged` collector is running before the
 * user signs in rather than after the first message that will never arrive.
 *
 * Reading the token is cheap and offline in the common case (FCM caches it), and idempotent: the
 * registrar's write is `merge = true`, so this racing a genuine `onNewToken` is harmless.
 */
class PushTokenBootstrap(
  private val sink: PushTokenSink,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  /** Seam for tests; production reads the real FCM registration token. */
  private val readToken: suspend () -> String? = ::firebaseMessagingToken,
) {

  init {
    scope.launch {
      val token = runCatching { readToken() }
        .onFailure { log.w(it) { "Could not read the current push token" } }
        .getOrNull()
      if (token.isNullOrEmpty()) return@launch
      runCatching { sink.onTokenRefreshed(token) }
        .onFailure { log.w(it) { "Could not forward the current push token" } }
    }
  }

  private companion object {
    val log = Logger.withTag("PushTokenBootstrap")
  }
}

private suspend fun firebaseMessagingToken(): String? = suspendCancellableCoroutine { cont ->
  FirebaseMessaging.getInstance().token
    .addOnSuccessListener { token -> cont.resume(token) }
    .addOnFailureListener { error -> cont.resumeWithException(error) }
}
