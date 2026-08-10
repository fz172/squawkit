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

    nonisolated func signIn() {
        Task { @MainActor in
            self.startSignIn()
        }
    }

    private func startSignIn() {
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
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
            ?? ASPresentationAnchor()
    }
}
