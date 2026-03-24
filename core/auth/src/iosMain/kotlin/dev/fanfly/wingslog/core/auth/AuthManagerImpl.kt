package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.OAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

class AuthManagerImpl(
    private val authProvider: FirebaseAuth,
) : AuthManager {

    override fun getCurrentUser(): FirebaseUser? = authProvider.currentUser

    /**
     * Tries to return the currently authenticated user without prompting.
     */
    override suspend fun trySilentLogin(): FirebaseUser? = authProvider.currentUser

    /**
     * Google Sign-In is not supported on iOS.
     * Returns null — use signInWithApple() on iOS instead.
     */
    override suspend fun signInWithGoogle(): FirebaseUser? {
        logger.w { "signInWithGoogle() is not supported on iOS" }
        return null
    }

    /**
     * Initiates Sign in with Apple using ASAuthorizationController,
     * then exchanges the Apple identity token for a Firebase user.
     */
    override suspend fun signInWithApple(): FirebaseUser? {
        return try {
            val (idToken, rawNonce) = requestAppleCredential()
            signInToFirebaseWithApple(idToken = idToken, rawNonce = rawNonce)
        } catch (e: Exception) {
            logger.e(e) { "Apple Sign-In failed" }
            null
        }
    }

    /**
     * Presents the Apple authorization sheet and suspends until the user
     * completes or cancels. Returns a pair of (idToken, rawNonce).
     */
    private suspend fun requestAppleCredential(): Pair<String, String> =
        suspendCancellableCoroutine { continuation ->
            val rawNonce = generateNonce()
            val hashedNonce = sha256Hex(rawNonce)

            val appleIDProvider = ASAuthorizationAppleIDProvider()
            val request = appleIDProvider.createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
                nonce = hashedNonce
            }

            val delegate = AppleSignInDelegate(
                rawNonce = rawNonce,
                onSuccess = { idToken -> continuation.resume(Pair(idToken, rawNonce)) },
                onError = { error -> continuation.resumeWithException(error) },
            )

            val authorizationController =
                ASAuthorizationController(authorizationRequests = listOf(request))
            authorizationController.delegate = delegate
            authorizationController.presentationContextProvider = delegate
            authorizationController.performRequests()
        }

    private suspend fun signInToFirebaseWithApple(
        idToken: String,
        rawNonce: String,
    ): FirebaseUser? {
        return try {
            val credential = OAuthProvider.credential(
                providerId = "apple.com",
                idToken = idToken,
                rawNonce = rawNonce,
                accessToken = null,
            )
            authProvider.signInWithCredential(credential)
            authProvider.currentUser
        } catch (e: Exception) {
            logger.e(e) { "Firebase sign-in with Apple credential failed" }
            null
        }
    }

    /**
     * Signs in anonymously using Firebase Authentication.
     * Does not interfere with [trySilentLogin] — if a user is already signed in
     * (including anonymously), this is a no-op and returns the current user.
     */
    override suspend fun signInAnonymously(): FirebaseUser? {
        if (authProvider.currentUser != null) {
            return authProvider.currentUser
        }
        return try {
            authProvider.signInAnonymously()
            authProvider.currentUser
        } catch (e: Exception) {
            logger.e(e) { "Anonymous sign-in failed" }
            null
        }
    }

    override suspend fun logOut() {
        try {
            authProvider.signOut()
        } catch (e: Exception) {
            logger.e(e) { "Error logging out" }
        }
    }

    companion object {
        private val logger = Logger.withTag("AuthManagerImpl-iOS")
    }
}

// --------------------------------------------------------------------------
// Apple Sign-In Delegate
// --------------------------------------------------------------------------

private class AppleSignInDelegate(
    private val rawNonce: String,
    private val onSuccess: (idToken: String) -> Unit,
    private val onError: (Exception) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential =
            didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (credential == null) {
            onError(IllegalStateException("Apple credential is null"))
            return
        }
        val identityTokenData = credential.identityToken
        if (identityTokenData == null) {
            onError(IllegalStateException("Apple identity token is null"))
            return
        }
        val idToken = identityTokenData.decodeToString()
        if (idToken == null) {
            onError(IllegalStateException("Could not decode Apple identity token to String"))
            return
        }
        onSuccess(idToken)
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        onError(Exception("Apple Sign-In error: ${didCompleteWithError.localizedDescription}"))
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor {
        @Suppress("DEPRECATION")
        return UIApplication.sharedApplication.keyWindow
            ?: (UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow)
            ?: UIWindow()
    }
}

// --------------------------------------------------------------------------
// Helpers: NSData -> String, nonce generation, SHA-256
// --------------------------------------------------------------------------

private fun NSData.decodeToString(): String? =
    NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()

private fun generateNonce(length: Int = 32): String {
    val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"
    return (1..length).map { charset.random() }.joinToString("")
}

@OptIn(ExperimentalForeignApi::class)
private fun sha256Hex(input: String): String {
    val inputBytes = input.encodeToByteArray()
    val digestLength = CC_SHA256_DIGEST_LENGTH
    val digestBytes = ByteArray(digestLength)
    inputBytes.usePinned { inputPinned ->
        digestBytes.usePinned { digestPinned ->
            CC_SHA256(
                data = inputPinned.addressOf(0),
                len = inputBytes.size.toUInt(),
                md = digestPinned.addressOf(0),
            )
        }
    }
    return digestBytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
