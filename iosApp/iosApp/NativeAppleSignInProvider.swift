import AuthenticationServices
import ComposeApp
import CryptoKit
import UIKit

/// Presents the system Sign in with Apple sheet and hands the credential material back to Kotlin.
///
/// Deliberately different from `NativeGoogleSignInProvider`, which signs in to Firebase itself and
/// reports only an error string: here Swift returns the identity token and raw nonce, and the
/// Kotlin `AuthManager` builds the Firebase credential. The account-upgrade merge path needs a real
/// `AuthCredential` on the Kotlin side, which a Swift-side sign-in could not hand back.
final class NativeAppleSignInProvider: NSObject {

    /// The un-hashed nonce for the in-flight request. Apple receives its SHA-256 inside the
    /// identity token's `nonce` claim; Firebase re-hashes this value and compares, which is what
    /// makes a captured token non-replayable. Kept only until the exchange completes.
    private var currentNonce: String?

    /// The window the sheet is anchored to, resolved before the request starts. Held so
    /// `presentationAnchor(for:)` can hand back a real window rather than building one.
    private var currentAnchor: ASPresentationAnchor?

    nonisolated func signIn() {
        Task { @MainActor in
            self.startSignIn()
        }
    }

    private func startSignIn() {
        // Resolve the anchor up front, the way NativeGoogleSignInProvider resolves its presenting
        // view controller: with no window there is nothing to present on, and reporting that
        // through the bridge is far better than starting a request that cannot be shown.
        guard let anchor = Self.presentationAnchor() else {
            complete(errorMessage: "No window is available to present Sign in with Apple")
            return
        }
        currentAnchor = anchor

        let nonce = Self.randomNonceString()
        currentNonce = nonce

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    private func complete(
        idToken: String? = nil,
        rawNonce: String? = nil,
        fullName: String? = nil,
        errorMessage: String? = nil,
        cancelled: Bool = false
    ) {
        currentNonce = nil
        currentAnchor = nil
        MainEntry.shared.completeAppleSignIn(
            idToken: idToken,
            rawNonce: rawNonce,
            fullName: fullName,
            errorMessage: errorMessage,
            cancelled: cancelled
        )
    }

    // MARK: - Nonce

    /// Apple requires the nonce to be a string of unreserved URL characters.
    private static func randomNonceString(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length

        while remaining > 0 {
            var random: UInt8 = 0
            let status = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
            guard status == errSecSuccess else {
                // SecRandomCopyBytes only fails if the system RNG is unavailable; there is no safe
                // fallback for a security nonce, so stop rather than emit a weak one.
                fatalError("Unable to generate a secure nonce: SecRandomCopyBytes failed (\(status))")
            }
            if random < charset.count {
                result.append(charset[Int(random)])
                remaining -= 1
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension NativeAppleSignInProvider: ASAuthorizationControllerDelegate {

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            complete(errorMessage: "Sign in with Apple returned an unexpected credential")
            return
        }
        guard let rawNonce = currentNonce else {
            complete(errorMessage: "Sign in with Apple completed without a pending nonce")
            return
        }
        guard let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            complete(errorMessage: "Sign in with Apple did not return an identity token")
            return
        }

        // Populated only on the very first authorization for this Apple ID. Kotlin writes it to the
        // Firebase profile on that one pass; afterwards Apple sends nil and it is gone for good.
        let fullName = credential.fullName.flatMap { name -> String? in
            let formatter = PersonNameComponentsFormatter()
            formatter.style = .long
            let formatted = formatter.string(from: name).trimmingCharacters(in: .whitespacesAndNewlines)
            return formatted.isEmpty ? nil : formatted
        }

        // `credential.email` is not forwarded: the `.email` scope is still requested so the address
        // lands in the identity token, and Firebase populates `FirebaseUser.email` from there.
        complete(
            idToken: idToken,
            rawNonce: rawNonce,
            fullName: fullName
        )
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        // A user-cancelled sheet is a normal outcome, not a failure: the caller maps it to
        // AccountUpgradeResult.Cancelled rather than showing an error.
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            complete(cancelled: true)
            return
        }
        complete(errorMessage: error.localizedDescription)
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension NativeAppleSignInProvider: ASAuthorizationControllerPresentationContextProviding {

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // startSignIn() resolves this before performRequests(), and the controller only asks once a
        // request is in flight, so a nil here means the request was started some other way.
        guard let anchor = currentAnchor else {
            preconditionFailure("Sign in with Apple asked for an anchor with no request in flight")
        }
        return anchor
    }

    /// Prefers the key window of a foreground-active scene, then any key window, then any window at
    /// all. Returns nil rather than constructing one: `ASPresentationAnchor` is a `UIWindow`, and a
    /// freshly built window belongs to no scene, so it could never host the sheet anyway — the old
    /// `?? ASPresentationAnchor()` fallback papered over a failure with something non-functional
    /// (and, as of iOS 26, deprecated).
    private static func presentationAnchor() -> ASPresentationAnchor? {
        let windowScenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let activeScenes = windowScenes.filter { $0.activationState == .foregroundActive }

        return activeScenes.flatMap(\.windows).first(where: \.isKeyWindow)
            ?? windowScenes.flatMap(\.windows).first(where: \.isKeyWindow)
            ?? windowScenes.flatMap(\.windows).first
    }
}
