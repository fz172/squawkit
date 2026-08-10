@file:JsModule("firebase/auth")
@file:JsNonModule

package dev.fanfly.wingslog.core.auth

import kotlin.js.Promise

// Minimal external bindings for the modular Firebase JS Auth SDK (v10). These operate on the
// same default-app Auth singleton that GitLive's Firebase.auth wraps, so after a popup sign-in
// the GitLive FirebaseAuth.currentUser reflects the signed-in user.

external fun getAuth(): dynamic

external fun signInWithPopup(auth: dynamic, provider: dynamic): Promise<dynamic>

external class GoogleAuthProvider

/**
 * Generic OAuth provider, used here for Apple (`"apple.com"`). Apple only returns the user's name
 * and email on the very first authorization, and only when the matching scopes were requested —
 * see [AuthManagerImpl.signInWithApple].
 */
external class OAuthProvider(providerId: String) {
  fun addScope(scope: String)
}
