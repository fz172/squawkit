package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Bridge for the iOS App Check token. `FirebaseAppCheck` is linked only in the Swift app target,
 * not the Kotlin/Native compilation, so the host installs a provider (via
 * `MainEntry.installAppCheckTokenProvider`) that fetches a token through the native SDK and hands
 * it back. Mirrors `IosGoogleSignInBridge`. Callback-shaped (not a single pending continuation) so
 * concurrent brokered downloads each get their own token fetch. Left uninstalled → `token()`
 * returns null and brokered downloads fail, same as the previous `Unavailable` stub.
 */
object IosAppCheckBridge {
  private var provider: ((onToken: (String?) -> Unit) -> Unit)? = null

  fun install(provider: (onToken: (String?) -> Unit) -> Unit) {
    this.provider = provider
  }

  internal suspend fun token(): String? {
    val p = provider
    if (p == null) {
      log.w { "App Check token requested but no provider installed (iOS host didn't wire it)" }
      return null
    }
    // Bound the wait: if the native completion never fires, the brokered download would otherwise
    // hang forever with no error — surfacing only as a distant "timed out". Time out to a null token
    // so it fails-and-retries, logged, instead.
    val token = withTimeoutOrNull(TOKEN_TIMEOUT) {
      suspendCancellableCoroutine { cont -> p { token -> cont.resume(token) } }
    }
    if (token == null) log.w { "App Check token was null/timed out after $TOKEN_TIMEOUT" }
    else log.d { "App Check token obtained (${token.length} chars)" }
    return token
  }

  private val log = Logger.withTag("IosAppCheckBridge")
  private val TOKEN_TIMEOUT = 10.seconds
}

/** iOS [AppCheckTokenProvider] backed by [IosAppCheckBridge]. */
class IosAppCheckTokenProvider : AppCheckTokenProvider {
  override suspend fun token(): String? = IosAppCheckBridge.token()
}
