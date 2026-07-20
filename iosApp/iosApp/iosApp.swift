import SwiftUI
import GoogleSignIn
import FirebaseCore
import FirebaseAppCheck
import ComposeApp


class AppDelegate: NSObject, UIApplicationDelegate {
  private let googleSignInProvider = NativeGoogleSignInProvider()

  func application(_ application: UIApplication,
                   didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
    // App Check must have a provider factory set BEFORE configure(), or every enforceAppCheck
    // callable (redeem invite, revoke, upload session, export) is rejected as "unauthenticated".
    // The debug provider prints a token to the console to register in the Firebase App Check console.
    // The Simulator can never attest via App Attest / DeviceCheck, so use the debug provider there
    // regardless of build config; a real release build falls through to the default App Attest.
    #if targetEnvironment(simulator) || DEBUG
    AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
    #endif
    FirebaseApp.configure()
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
        #if DOGFOOD
        MainEntry.shared.doInitKoin(forceDeveloperBuild: true)
        #else
        MainEntry.shared.doInitKoin(forceDeveloperBuild: false)
        #endif
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
