package dev.fanfly.wingslog

import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.auth.IosAppleSignInBridge
import dev.fanfly.wingslog.core.auth.IosGoogleSignInBridge
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
import dev.fanfly.wingslog.feature.ads.datamanager.impl.IosAdConsentBridge
import dev.fanfly.wingslog.feature.ads.viewing.IosAdViewBridge
import dev.fanfly.wingslog.feature.notifications.engine.BgTaskUrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink
import dev.fanfly.wingslog.feature.notifications.viewing.IosNotificationTapDelegate
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.blob.IosAppCheckBridge
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UserNotifications.UNUserNotificationCenter
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
   * Installs the background-only consent-info resolver owned by the Swift app (Google UMP is a
   * Swift-side concern; see `IosAdConsentBridge`) — no UI, just `requestConsentInfoUpdate`.
   * [provider] receives the Developer Options test-device hash (or `null`) and a callback that
   * must be called with `"REQUIRED"`/`"NOT_REQUIRED"`. Lets a caller (onboarding) decide whether to
   * put a priming explanation in front of the real dialog before calling
   * [installConsentFormPresenter] to show it.
   */
  fun installConsentInfoUpdateProvider(provider: (testDeviceHashedId: String?, onResult: (String) -> Unit) -> Unit) {
    IosAdConsentBridge.installConsentInfoUpdateProvider(provider)
  }

  /**
   * Installs the resolver that actually shows the CMP dialog if required. [provider] receives the
   * Developer Options test-device hash (or `null`) and a callback that must be called with one of
   * `"NON_PERSONALIZED"`/`"DENIED"` (iOS never resolves `"PERSONALIZED"` — see `AdConsentManager`'s
   * KDoc on why ATT was dropped).
   */
  fun installConsentFormPresenter(presenter: (testDeviceHashedId: String?, onResult: (String) -> Unit) -> Unit) {
    IosAdConsentBridge.installConsentFormPresenter(presenter)
  }

  /** Installs the Settings → "Ad privacy settings" re-presentation of the CMP form. */
  fun installAdPrivacyOptionsPresenter(presenter: (onComplete: () -> Unit) -> Unit) {
    IosAdConsentBridge.installPrivacyOptionsPresenter(presenter)
  }

  /**
   * Installs the synchronous check backing "Ad privacy settings"' own visibility — whether
   * `ConsentInformation.shared.privacyOptionsRequirementStatus` currently reads `.required`, so the
   * row can hide itself rather than presenting a control with nothing to show.
   */
  fun installIsPrivacyOptionsAvailableProvider(provider: () -> Boolean) {
    IosAdConsentBridge.installPrivacyOptionsAvailableProvider(provider)
  }

  /**
   * Installs the Developer Options "Reset ad consent" action — wipes UMP's on-device cache so the
   * onboarding priming explainer can be re-tested without clearing the app's local data/account.
   */
  fun installResetConsentAction(action: () -> Unit) {
    IosAdConsentBridge.installResetConsentAction(action)
  }

  /**
   * Installs the ad view factory owned by the Swift app (`GoogleMobileAds` is linked there via
   * SPM, not in Kotlin/Native — see `IosAdViewBridge`'s KDoc). [factory] builds a configured
   * `GADBannerView` for the given ad unit id and size id (`"BANNER"`/`"LARGE_BANNER"`), wires its
   * delegate to the three callbacks, and returns it as a `UIView` — or `null` if it fails to build,
   * which collapses the slot exactly like an unfilled one.
   */
  fun installAdViewFactory(
    factory: (
      adUnitId: String,
      sizeId: String,
      onFilled: () -> Unit,
      onFailed: (reason: String) -> Unit,
      onClicked: () -> Unit,
    ) -> UIView?
  ) {
    IosAdViewBridge.install(factory)
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
    // An thing-share invite is parked for the redeem flow; otherwise fall through to email sign-in.
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
   * Installs the `UNUserNotificationCenter` delegate that routes a tapped notification into the app
   * (design §5.3), and lets urgency banners show while the app is foregrounded. Must be called
   * before `application:didFinishLaunchingWithOptions:` returns — iOS drops the response for a tap
   * that cold-started the process if no delegate is set by then, which is exactly the case that
   * matters (a tap from the lock screen).
   *
   * The delegate is held here rather than by Swift because `UNUserNotificationCenter.delegate` is a
   * weak reference; a locally-created instance would be collected and taps would silently stop.
   */
  fun registerNotificationTapHandler() {
    UNUserNotificationCenter.currentNotificationCenter()
      .setDelegate(notificationTapDelegate)
  }

  private val notificationTapDelegate = IosNotificationTapDelegate()

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

  /**
   * Registers the N2 urgency scan's [BGAppRefreshTask] and submits the first request (design §5.4).
   * Registration must happen before `application:didFinishLaunchingWithOptions:` returns, and the
   * identifier `dev.fanfly.wingslog.urgency-scan` must appear in `Info.plist`'s
   * `BGTaskSchedulerPermittedIdentifiers`.
   *
   * Submitting here rather than from a Koin `createdAtStart` single (which is what Android does)
   * keeps the order right: a submission for an unregistered identifier is rejected.
   */
  fun registerUrgencyScanTask() {
    val scheduler = KoinPlatform.getKoin()
      .get<BgTaskUrgencyScanScheduler>()
    scheduler.registerBgTask()
    scheduler.ensureScheduled()
  }

  /**
   * Forwards an FCM registration token from Swift (design §7.1, issue #506) —
   * `FirebaseMessaging` is a third-party framework Kotlin/Native can't link, so `iosApp.swift`'s
   * `AppDelegate` owns the SDK (both the proactive read at launch and the `MessagingDelegate`
   * rotation callback) and calls this for either case. [PushTokenSink] already exists in `:model`
   * for exactly this hand-off; no new bridge object needed since there is nothing to hand back to
   * Swift, unlike [installAppCheckTokenProvider]'s request/response shape.
   */
  fun onPushTokenReceived(token: String) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        KoinPlatform.getKoin().get<PushTokenSink>().onTokenRefreshed(token)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        log.w(e) { "Could not forward a push token from Swift" }
      }
    }
  }

  private val log = Logger.withTag("MainEntry")
}
