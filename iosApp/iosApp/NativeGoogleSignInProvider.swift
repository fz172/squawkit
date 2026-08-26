import ComposeApp
import FirebaseCore
import GoogleSignIn
import UIKit

/// Presents the Google Sign-In sheet and hands the credential material back to Kotlin.
///
/// It used to complete the Firebase sign-in here in Swift and report only an error string, which
/// is why Google was missing from the guest upgrade picker on iOS: linking a guest session needs a
/// real `AuthCredential` on the Kotlin side, and a Swift-side sign-in cannot hand one back — it
/// would have replaced the anonymous user instead of linking to it, losing the UID every local row
/// is keyed to. This now mirrors `NativeAppleSignInProvider`: Swift authorizes, Kotlin builds the
/// credential and decides whether to sign in or link.
final class NativeGoogleSignInProvider: NSObject {
    nonisolated func signIn() {
        Task { @MainActor in
            self.startSignIn()
        }
    }

    private func startSignIn() {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            complete(errorMessage: "Firebase Google client ID is not configured")
            return
        }
        guard let presentingViewController = Self.presentingViewController() else {
            complete(errorMessage: "No view controller is available to present Google Sign-In")
            return
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) { result, error in
            if let error {
                // A dismissed sheet is a normal outcome, not a failure: Kotlin maps it to
                // AccountUpgradeResult.Cancelled rather than showing an error, the same as Apple's.
                let nsError = error as NSError
                if nsError.domain == kGIDSignInErrorDomain,
                   nsError.code == GIDSignInError.canceled.rawValue {
                    self.complete(cancelled: true)
                    return
                }
                self.complete(errorMessage: error.localizedDescription)
                return
            }
            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                self.complete(errorMessage: "Google Sign-In did not return an ID token")
                return
            }

            // Firebase's Google credential needs both halves; `accessToken` is non-optional on a
            // successful authorization, unlike `idToken`.
            self.complete(
                idToken: idToken,
                accessToken: user.accessToken.tokenString
            )
        }
    }

    private func complete(
        idToken: String? = nil,
        accessToken: String? = nil,
        errorMessage: String? = nil,
        cancelled: Bool = false
    ) {
        MainEntry.shared.completeGoogleSignIn(
            idToken: idToken,
            accessToken: accessToken,
            errorMessage: errorMessage,
            cancelled: cancelled
        )
    }

    private static func presentingViewController() -> UIViewController? {
        let rootViewController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        return topViewController(from: rootViewController)
    }

    private static func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let presented = viewController?.presentedViewController {
            return topViewController(from: presented)
        }
        if let navigation = viewController as? UINavigationController {
            return topViewController(from: navigation.visibleViewController)
        }
        if let tab = viewController as? UITabBarController {
            return topViewController(from: tab.selectedViewController)
        }
        return viewController
    }
}
