package dev.fanfly.wingslog

import androidx.compose.ui.window.ComposeUIViewController
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.auth.IosAppleSignInBridge
import dev.fanfly.wingslog.core.auth.IosGoogleSignInBridge
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.blob.IosAppCheckBridge
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

object MainEntry {
  fun mainViewController(): UIViewController = ComposeUIViewController {
    AppEntry()
  }

  @OptIn(ExperimentalNativeApi::class)
  fun doInitKoin(forceDeveloperBuild: Boolean = false) {
    initKoin(isDeveloperBuild = forceDeveloperBuild || Platform.isDebugBinary) {}
    runTombstoneGc()
  }

  fun installGoogleSignInHandler(handler: () -> Unit) {
    IosGoogleSignInBridge.install(handler)
  }

  /**
   * Installs the Sign in with Apple presenter owned by the Swift app (`AuthenticationServices` and
   * the nonce hashing live there, not in Kotlin/Native). As with the Google handler, Swift returns
   * the credential material rather than completing the sign-in, so Kotlin can build an
   * `AuthCredential` — the account-upgrade link and merge paths both need one.
   */
  fun installAppleSignInHandler(handler: () -> Unit) {
    IosAppleSignInBridge.install(handler)
  }

  /**
   * Installs the App Check token fetch owned by the Swift app (`FirebaseAppCheck` is linked there,
   * not in Kotlin/Native). [provider] is invoked with a callback that must be called with a fresh
   * App Check token (or null). Used by the attachment broker's `streamBlob` download header.
   */
  fun installAppCheckTokenProvider(provider: (onToken: (String?) -> Unit) -> Unit) {
    IosAppCheckBridge.install(provider)
  }

  /**
   * Completes a pending Google Sign-In. Swift hands back both tokens rather than signing in itself;
   * Firebase's Google credential is built from the pair, and the guest upgrade links it (see
   * `IosGoogleSignInBridge`).
   */
  fun completeGoogleSignIn(
    idToken: String?,
    accessToken: String?,
    errorMessage: String?,
    cancelled: Boolean,
  ) {
    IosGoogleSignInBridge.complete(
      idToken = idToken,
      accessToken = accessToken,
      errorMessage = errorMessage,
      cancelled = cancelled,
    )
  }

  /**
   * Completes a pending Sign in with Apple. [rawNonce] is the un-hashed nonce whose SHA-256 was
   * sent to Apple; Firebase re-hashes it to validate the identity token. [fullName] is non-null
   * only on the user's first authorization of this app.
   */
  fun completeAppleSignIn(
    idToken: String?,
    rawNonce: String?,
    fullName: String?,
    errorMessage: String?,
    cancelled: Boolean,
  ) {
    IosAppleSignInBridge.complete(
      idToken = idToken,
      rawNonce = rawNonce,
      fullName = fullName,
      errorMessage = errorMessage,
      cancelled = cancelled,
    )
  }

  /**
   * Forwards an inbound URL (Universal Link / custom scheme from `onOpenURL`) into the shared auth
   * flow so a passwordless email sign-in link completes. Returns true when the URL was an email
   * sign-in link (so Swift can stop handing it to other handlers). See
   * docs/account/email_link_signin_design.html.
   */
  fun handleIncomingUrl(url: String): Boolean {
    // An aircraft-share invite is parked for the redeem flow; otherwise fall through to email sign-in.
    if (AircraftShareDeepLinks.deliver(url)) return true
    val authManager = KoinPlatform.getKoin()
      .get<dev.fanfly.wingslog.core.auth.AuthManager>()
    if (!authManager.isSignInWithEmailLink(url)) return false
    EmailLinkDeepLinks.deliver(url)
    return true
  }

  fun startSyncEngine() {
    KoinPlatform.getKoin()
      .get<SyncEngine>()
      .start()
  }

  // Best-effort startup GC; runOnce() is now suspend (async-generated queries).
  private fun runTombstoneGc() {
    CoroutineScope(Dispatchers.Default).launch {
      KoinPlatform.getKoin()
        .get<TombstoneGc>()
        .runOnce()
    }
  }

  /**
   * Registers the background blob-scan [BGProcessingTask] with the OS. Must be called before
   * `application:didFinishLaunchingWithOptions:` returns. The task identifier
   * `dev.fanfly.wingslog.blob-scan` must appear in `Info.plist`'s
   * `BGTaskSchedulerPermittedIdentifiers`.
   */
  fun registerBgTasks() {
    KoinPlatform.getKoin()
      .get<UrlSessionUploadScheduler>()
      .registerBgTasks()
  }
}
