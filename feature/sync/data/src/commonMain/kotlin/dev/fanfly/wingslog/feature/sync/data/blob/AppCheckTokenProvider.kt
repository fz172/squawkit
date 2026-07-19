package dev.fanfly.wingslog.feature.sync.data.blob

/**
 * Supplies a fresh Firebase App Check token for the `streamBlob` download proxy, which requires an
 * `X-Firebase-AppCheck` header (it verifies App Check itself rather than through `enforceAppCheck`,
 * because it is an `onRequest` HTTP function, not a callable). GitLive exposes no common App Check
 * API, so each platform binds its native SDK: Android `FirebaseAppCheck.getAppCheckToken`, web
 * `getToken(appCheck)`, iOS `FIRAppCheck.token` (fast-follow).
 *
 * The upload path does NOT use this — its `getBlobUploadSession` call is a GitLive callable, which
 * attaches App Check automatically through the native SDK.
 */
interface AppCheckTokenProvider {
  /**
   * A current App Check token, or `null` when unavailable (App Check not configured on this
   * platform yet, or token fetch failed). A `null` makes the broker download fail and retry rather
   * than send an unauthenticated request the proxy would reject with `401` anyway.
   */
  suspend fun token(): String?

  /** No-op provider: every download through the broker fails until a real provider is wired. */
  object Unavailable : AppCheckTokenProvider {
    override suspend fun token(): String? = null
  }
}
