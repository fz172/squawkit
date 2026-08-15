import SwiftUI
import GoogleSignIn
import FirebaseCore
import FirebaseAppCheck
import AppTrackingTransparency
import ComposeApp


/// App Check provider factory for real builds — attests app integrity with a Secure Enclave key
/// via `DCAppAttestService`. Firebase ships `AppAttestProvider` but no matching factory (unlike
/// `AppCheckDebugProviderFactory` and `DeviceCheckProviderFactory`), so supply one.
final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {
  func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
    AppAttestProvider(app: app)
  }
}

class AppDelegate: NSObject, UIApplicationDelegate {
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
    // ATT is real; the UMP half of consent resolution is TODO(#385/P8) — Google's UMP SDK ships in
    // the same googleads-mobile-sdk-ios SPM package P8 adds for GADBannerView, which isn't linked on
    // this target yet. Until then this reports PERSONALIZED/NON_PERSONALIZED off ATT alone, which is
    // safe: AdView.ios.kt is still a no-op placeholder, so nothing here gates a real ad request yet.
    MainEntry.shared.installAdConsentProvider { onResult in
      ATTrackingManager.requestTrackingAuthorization { status in
        DispatchQueue.main.async {
          switch status {
          case .authorized:
            onResult("PERSONALIZED")
          default:
            onResult("NON_PERSONALIZED")
          }
        }
      }
    }
    MainEntry.shared.installAdPrivacyOptionsPresenter { onComplete in
      // TODO(#385/P8): present the UMP privacy-options form once the SPM package is linked.
      onComplete()
    }
    // Register BGProcessingTask identifier "dev.fanfly.wingslog.blob-scan" with the OS.
    // Must be called before this method returns.
    MainEntry.shared.registerBgTasks()
    return true
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
