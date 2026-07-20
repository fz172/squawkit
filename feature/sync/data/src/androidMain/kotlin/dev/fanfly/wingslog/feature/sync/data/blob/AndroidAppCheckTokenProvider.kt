package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android [AppCheckTokenProvider]: fetches a token from the App Check provider installed in
 * `WingsLogApplication` (Play Integrity in release, debug provider otherwise). Wrapped in a
 * [suspendCancellableCoroutine] so we don't pull in `kotlinx-coroutines-play-services` just to
 * `await()` one Task. Any failure resolves to `null`, degrading the brokered download to a retry
 * rather than an unauthenticated request.
 */
class AndroidAppCheckTokenProvider : AppCheckTokenProvider {

  private val log = Logger.withTag("AppCheckTokenProvider")

  override suspend fun token(): String? =
    try {
      suspendCancellableCoroutine { cont ->
        FirebaseAppCheck.getInstance()
          .getAppCheckToken(false)
          .addOnSuccessListener { cont.resume(it.token) }
          .addOnFailureListener { e ->
            log.w(e) { "App Check token fetch failed" }
            cont.resume(null)
          }
      }
    } catch (e: Exception) {
      log.w(e) { "App Check token fetch threw" }
      null
    }
}
