package dev.fanfly.wingslog.feature.sync.data.blob

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Bridge for the web App Check token. The `firebase/app-check` `getToken` binding and the `AppCheck`
 * instance both live in the `webApp` host (beside `initializeAppCheck`), so the host sets
 * [tokenSource] once App Check is initialised. Kept as an injectable hook rather than a direct
 * `@JsModule` binding here so this module stays free of web-SDK externals. Left `null` when App Check
 * is not configured (e.g. `APP_CHECK_SITE_KEY` unset in local dev), which makes brokered downloads
 * fail-and-retry instead of sending an unauthenticated request.
 */
object WebAppCheckBridge {
  var tokenSource: (() -> Promise<String?>)? = null
}

/** Web [AppCheckTokenProvider] backed by [WebAppCheckBridge]. */
class WebAppCheckTokenProvider : AppCheckTokenProvider {
  override suspend fun token(): String? =
    try {
      WebAppCheckBridge.tokenSource?.invoke()?.await()
    } catch (e: Throwable) {
      null
    }
}
