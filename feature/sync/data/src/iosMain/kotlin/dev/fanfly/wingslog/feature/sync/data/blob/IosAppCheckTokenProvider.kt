package dev.fanfly.wingslog.feature.sync.data.blob

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
    val p = provider ?: return null
    return suspendCancellableCoroutine { cont -> p { token -> cont.resume(token) } }
  }
}

/** iOS [AppCheckTokenProvider] backed by [IosAppCheckBridge]. */
class IosAppCheckTokenProvider : AppCheckTokenProvider {
  override suspend fun token(): String? = IosAppCheckBridge.token()
}
