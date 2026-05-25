import ComposeApp
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import UIKit

final class NativeGoogleSignInProvider: NSObject {
    nonisolated func signIn() {
        Task { @MainActor in
            self.startSignIn()
        }
    }

    private func startSignIn() {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            complete("Firebase Google client ID is not configured")
            return
        }
        guard let presentingViewController = Self.presentingViewController() else {
            complete("No view controller is available to present Google Sign-In")
            return
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) { result, error in
            if let error {
                self.complete(error.localizedDescription)
                return
            }
            guard let user = result?.user,
                  let idToken = user.idToken?.tokenString else {
                self.complete("Google Sign-In did not return an ID token")
                return
            }

            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: user.accessToken.tokenString
            )
            Auth.auth().signIn(with: credential) { _, error in
                self.complete(error?.localizedDescription)
            }
        }
    }

    private func complete(_ errorMessage: String?) {
        MainEntry.shared.completeGoogleSignIn(errorMessage: errorMessage)
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
