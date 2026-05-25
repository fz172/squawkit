import SwiftUI
import GoogleSignIn
import FirebaseCore
import ComposeApp


class AppDelegate: NSObject, UIApplicationDelegate {
  private let googleSignInProvider = NativeGoogleSignInProvider()

  func application(_ application: UIApplication,
                   didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
    FirebaseApp.configure()
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
        MainEntry.shared.doInitKoinDogfood()
        #else
        MainEntry.shared.doInitKoin()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
