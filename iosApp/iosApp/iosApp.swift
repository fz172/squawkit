import SwiftUI
import GoogleSignIn
import FirebaseCore
import FirebaseAppCheck
import FirebaseMessaging
import ComposeApp


/// App Check provider factory for real builds — attests app integrity with a Secure Enclave key
/// via `DCAppAttestService`. Firebase ships `AppAttestProvider` but no matching factory (unlike
/// `AppCheckDebugProviderFactory` and `DeviceCheckProviderFactory`), so supply one.
final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {
  func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
    AppAttestProvider(app: app)
  }
}

class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate {
  private let googleSignInProvider = NativeGoogleSignInProvider()
  private let appleSignInProvider = NativeAppleSignInProvider()

  func application(_ application: UIApplication,
                   didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
    // Hand the attachment broker (Kotlin/Native, which can't link FirebaseAppCheck) a way to mint
    // App Check tokens for the streamBlob download header.
    MainEntry.shared.installAppCheckTokenProvider { onToken in
      AppCheck.appCheck().token(forcingRefresh: false) { token, _ in
        onToken(token?.token)
      }
    }
    MainEntry.shared.startSyncEngine()
    MainEntry.shared.installGoogleSignInHandler { [weak self] in
      self?.googleSignInProvider.signIn()
    }
    MainEntry.shared.installAppleSignInHandler { [weak self] in
      self?.appleSignInProvider.signIn()
    }
    // Real Google UMP, no ATT by product decision (P8 links googleads-mobile-sdk-ios, which pulls
    // UserMessagingPlatform in transitively) — installs both installAdConsentProvider and
    // installAdPrivacyOptionsPresenter. See AdConsentPresenter.swift.
    installAdConsentProvider()
    // GoogleMobileAds is now linked (P8) — installs MainEntry.installAdViewFactory. See
    // AdViewFactory.swift.
    installAdViewFactory()
    // Register BGProcessingTask identifier "dev.fanfly.wingslog.blob-scan" with the OS.
    // Must be called before this method returns.
    MainEntry.shared.registerBgTasks()
    // Routes a tapped notification into NotificationTapRouter, and lets urgency banners show while
    // the app is foregrounded. Also must be set before this method returns, or iOS drops the
    // response for a tap that cold-started the app — the case that matters most.
    MainEntry.shared.registerNotificationTapHandler()
    // Register BGAppRefreshTask identifier "dev.fanfly.wingslog.urgency-scan" and submit the first
    // request. Also before this method returns — BGTaskScheduler rejects a late registration.
    MainEntry.shared.registerUrgencyScanTask()

    // N1 push registration (design §7.1, issue #506). FirebaseMessaging is linked only here, not in
    // Kotlin/Native — same reasoning as FirebaseAppCheck/GoogleSignIn: a third-party framework Kotlin
    // can't link, so Swift owns the SDK and forwards the result through MainEntry.onPushTokenReceived.
    Messaging.messaging().delegate = self
    // Safe regardless of notification-permission state — a device token exists independently of
    // whether the user has granted alert permission (that's IosNotificationPermission's concern, not
    // this call's). Registering unconditionally means a later permission grant needs nothing extra.
    application.registerForRemoteNotifications()
    // Proactively read the *current* token, mirroring Android's PushTokenBootstrap: the
    // didReceiveRegistrationToken delegate callback only fires on first mint or rotation, so a device
    // that already has a token before this code shipped would otherwise never register at all.
    Messaging.messaging().token { token, _ in
      guard let token else { return }
      MainEntry.shared.onPushTokenReceived(token: token)
    }
    return true
  }

  // FCM's APNs bridge: hands the raw APNs device token to Messaging so it can mint/attach the FCM
  // registration token our backend's Admin SDK actually sends to (sendEachForMulticast needs FCM
  // tokens, not raw APNs ones).
  func application(
    _ application: UIApplication,
    didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
  ) {
    Messaging.messaging().apnsToken = deviceToken
  }

  func application(
    _ application: UIApplication,
    didFailToRegisterForRemoteNotificationsWithError error: Error
  ) {
    // No user-facing failure path needed — same fire-and-forget shape as PushTokenBootstrap
    // swallowing a read failure on Android. The next successful registration (app relaunch, network
    // recovery) closes the gap on its own.
  }

  // MessagingDelegate: fires once a token is first minted and again on every rotation — FCM's
  // equivalent of Android's onNewToken. token is the FCM registration token, already resolved
  // against apnsToken above.
  func messaging(_ messaging: Messaging, didReceiveRegistrationToken token: String?) {
    guard let token else { return }
    MainEntry.shared.onPushTokenReceived(token: token)
  }

  // Called when iOS relaunches the app to deliver background URLSession completion events.
  // Re-creating the URLSession with the same identifier (done lazily in UrlSessionUploadScheduler
  // during doInitKoin) reconnects to the in-flight tasks and delivers results to the delegate.
  func application(
    _ application: UIApplication,
    handleEventsForBackgroundURLSession identifier: String,
    completionHandler: @escaping () -> Void
  ) {
    // The session is already reconnected during doInitKoin(). Call the handler immediately
    // so iOS can take a new snapshot. For full optimization, store the handler and call it
    // in URLSessionDidFinishEventsForBackgroundURLSession.
    completionHandler()
  }
}

@main
struct iosApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    init() {
        // Firebase must be configured here, not in the AppDelegate: SwiftUI runs the @main struct's
        // init() *before* application(_:didFinishLaunchingWithOptions:), and doInitKoin() starts
        // Koin's eager singletons — BillingIdentityCoordinator resolves FirebaseAuth at that point,
        // which fatal-errors if no default FirebaseApp exists yet.
        //
        // App Check must have a provider factory set BEFORE configure(), or every enforceAppCheck
        // callable (redeem invite, revoke, upload session, export) is rejected as "unauthenticated".
        // There is no implicit default: with no factory set, FIRAppCheck refuses to instantiate at
        // all (FIRAppCheck.m logs "without a provider factory" and returns nil), so both branches
        // below have to be spelled out.
        //
        // The Simulator can never attest via App Attest / DeviceCheck, so use the debug provider
        // there regardless of build config. It prints a token to the console that has to be
        // registered in Firebase Console -> App Check -> Manage debug tokens, per install.
        #if targetEnvironment(simulator) || DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif
        FirebaseApp.configure()

        // Developer tooling turns itself on for a debug binary — doInitKoin ORs this argument with
        // Kotlin's Platform.isDebugBinary, which Swift can't see. Debug and Release are the only two
        // configurations, mirroring Android's debug/release build types.
        MainEntry.shared.doInitKoin(forceDeveloperBuild: false)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Google sign-in first; otherwise try a passwordless email sign-in link.
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    _ = MainEntry.shared.handleIncomingUrl(url: url.absoluteString)
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    // Universal Links (apple-app-site-association for applinks:squawkit.fanfly.dev).
                    if let url = activity.webpageURL {
                        _ = MainEntry.shared.handleIncomingUrl(url: url.absoluteString)
                    }
                }
        }
    }
}
