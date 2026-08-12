package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser

class AuthManagerImpl(
  private val authProvider: FirebaseAuth,
) : AuthManager {

  private val emailLink = EmailLinkAuthenticator(authProvider)

  override fun getCurrentUser(): FirebaseUser? = authProvider.currentUser

  override suspend fun sendSignInLink(email: String): SendLinkResult =
    emailLink.sendSignInLink(email)

  override fun isSignInWithEmailLink(link: String): Boolean =
    emailLink.isSignInWithEmailLink(link)

  override suspend fun completeSignInLink(
    email: String,
    link: String
  ): FirebaseUser? =
    emailLink.completeSignInLink(email, link)

  /**
   * Tries to return the currently authenticated user without prompting.
   */
  override suspend fun trySilentLogin(): FirebaseUser? =
    authProvider.currentUser

  /**
   * Sign in with Google, via the native `GIDSignIn` sheet presented by the Swift app.
   *
   * Swift returns the tokens and the Firebase exchange happens here, the same shape as
   * [signInWithApple] — see [IosGoogleSignInBridge] for why the sign-in is no longer done natively.
   */
  override suspend fun signInWithGoogle(): FirebaseUser? {
    val result = IosGoogleSignInBridge.signIn()
    if (result.cancelled) {
      logger.d { "Google sign-in cancelled by user" }
      return null
    }
    if (result.errorMessage != null) {
      logger.w { "Google sign-in failed: ${result.errorMessage}" }
      return null
    }
    val credential = result.toCredential() ?: run {
      logger.w { "Google sign-in returned no usable token pair" }
      return null
    }

    return try {
      val signInResult = authProvider.signInWithCredential(credential)
      signInResult.user ?: authProvider.currentUser.also { user ->
        if (user == null) {
          logger.w { "Google sign-in completed without a Firebase user" }
        }
      }
    } catch (e: Exception) {
      logger.e(e) { "Firebase sign-in with the Google credential failed" }
      null
    }
  }

  /**
   * Sign in with Apple, via the native `ASAuthorization` sheet presented by the Swift app.
   *
   * Apple returns the user's name only on the **first** authorization for this Apple ID, and
   * Firebase never populates `displayName` from Apple — so capture it here on that one pass, or
   * the ID token carries no `name` and Cloud Functions stamp share invites with a blank `hostName`
   * (see [updateDisplayName]).
   */
  override suspend fun signInWithApple(): FirebaseUser? {
    val result = IosAppleSignInBridge.signIn()
    if (result.cancelled) {
      logger.d { "Sign in with Apple cancelled by user" }
      return null
    }
    if (result.errorMessage != null) {
      logger.w { "Sign in with Apple failed: ${result.errorMessage}" }
      return null
    }
    val credential = result.toCredential() ?: run {
      logger.w { "Sign in with Apple returned no identity token" }
      return null
    }

    return try {
      val signInResult = authProvider.signInWithCredential(credential)
      val user = signInResult.user ?: authProvider.currentUser
      if (user == null) {
        logger.w { "Sign in with Apple completed without a Firebase user" }
        return null
      }
      captureAppleName(user, result.fullName)
      user
    } catch (e: Exception) {
      logger.e(e) { "Firebase sign-in with the Apple credential failed" }
      null
    }
  }

  /**
   * Best-effort: writes Apple's first-authorization [fullName] to the auth profile, never
   * overwriting a name the account already has.
   */
  private suspend fun captureAppleName(user: FirebaseUser, fullName: String?) {
    val name = fullName?.takeIf { it.isNotBlank() } ?: return
    if (!user.displayName.isNullOrBlank()) return
    updateDisplayName(name)
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

  /**
   * Links the chosen provider's credential to the current anonymous user, preserving the UID so
   * every local row stays valid with zero migration. On a collision (that account already exists)
   * the caller is handed the merge path instead.
   *
   * Mirrors the Android implementation; see docs/account/account_upgrade_design.html.
   */
  override suspend fun upgradeAnonymousAccount(
    provider: AuthProvider,
  ): AccountUpgradeResult = when (provider) {
    AuthProvider.Apple -> upgradeWithApple()
    AuthProvider.Google -> upgradeWithGoogle()

    AuthProvider.Email -> AccountUpgradeResult.Failed(
      "Email upgrade completes through completeUpgradeWithEmailLink"
    )
  }

  override suspend fun completeUpgradeWithEmailLink(
    email: String,
    link: String,
  ): AccountUpgradeResult = emailLink.linkToCurrentUser(email, link)

  /**
   * The Google half of [upgradeAnonymousAccount].
   *
   * Unlike [upgradeWithApple], the collision path returns [AccountUpgradeResult.CredentialInUse]
   * rather than [AccountUpgradeResult.ReauthRequiredToMerge]: a Google ID token is not nonce-bound
   * and single-use, so the same credential the failed link consumed can back the merge sign-in —
   * no second account picker.
   */
  private suspend fun upgradeWithGoogle(): AccountUpgradeResult {
    val current = authProvider.currentUser
      ?: return AccountUpgradeResult.Failed("No signed-in user to upgrade")

    val result = IosGoogleSignInBridge.signIn()
    if (result.cancelled) {
      logger.d { "Account upgrade cancelled by user" }
      return AccountUpgradeResult.Cancelled
    }
    if (result.errorMessage != null) {
      logger.e { "Account upgrade: Google authorization failed: ${result.errorMessage}" }
      return AccountUpgradeResult.Failed(result.errorMessage)
    }
    val credential = result.toCredential()
      ?: return AccountUpgradeResult.Failed("Could not read the Google credential")

    return try {
      val linkResult = current.linkWithCredential(credential)
      AccountUpgradeResult.Linked(linkResult.user ?: authProvider.currentUser ?: current)
    } catch (e: FirebaseAuthUserCollisionException) {
      logger.i { "Google account already in use; offering merge" }
      AccountUpgradeResult.CredentialInUse(credential)
    } catch (e: Exception) {
      logger.e(e) { "Account upgrade: linking failed" }
      AccountUpgradeResult.Failed(e.message ?: "Linking failed")
    }
  }

  private suspend fun upgradeWithApple(): AccountUpgradeResult {
    val current = authProvider.currentUser
      ?: return AccountUpgradeResult.Failed("No signed-in user to upgrade")

    val result = IosAppleSignInBridge.signIn()
    if (result.cancelled) {
      logger.d { "Account upgrade cancelled by user" }
      return AccountUpgradeResult.Cancelled
    }
    if (result.errorMessage != null) {
      logger.e { "Account upgrade: Apple authorization failed: ${result.errorMessage}" }
      return AccountUpgradeResult.Failed(result.errorMessage)
    }
    val credential = result.toCredential()
      ?: return AccountUpgradeResult.Failed("Could not read the Apple credential")

    return try {
      val linkResult = current.linkWithCredential(credential)
      val user = linkResult.user ?: authProvider.currentUser ?: current
      captureAppleName(user, result.fullName)
      AccountUpgradeResult.Linked(user)
    } catch (e: FirebaseAuthUserCollisionException) {
      logger.i { "Apple account already in use; merge needs a fresh authorization" }
      AccountUpgradeResult.ReauthRequiredToMerge(AuthProvider.Apple)
    } catch (e: Exception) {
      logger.e(e) { "Account upgrade: linking failed" }
      AccountUpgradeResult.Failed(e.message ?: "Linking failed")
    }
  }

  /**
   * Runs a *second* Apple authorization and signs in to the account that already owns this Apple ID.
   *
   * An Apple identity token is single-use and bound to the nonce it was issued for, so the one the
   * failed `linkWithCredential` consumed cannot be replayed — Firebase rejects it with
   * `ERROR_MISSING_OR_INVALID_NONCE` ("Duplicate credential received"). Google's ID token can back a
   * second credential, which is why only this path needs a re-prompt.
   *
   * The caller shows the user why before this runs; the sheet itself is normally a Face ID
   * confirmation, since the app is already authorized for the Apple ID.
   */
  override suspend fun mergeIntoExistingAccount(
    provider: AuthProvider,
  ): AccountUpgradeResult {
    if (provider != AuthProvider.Apple) {
      return AccountUpgradeResult.Failed("Only Sign in with Apple re-authorizes to merge on iOS")
    }

    val retry = IosAppleSignInBridge.signIn()
    if (retry.cancelled) {
      logger.d { "Merge cancelled at the second Apple authorization" }
      return AccountUpgradeResult.Cancelled
    }
    if (retry.errorMessage != null) {
      logger.e { "Merge: second Apple authorization failed: ${retry.errorMessage}" }
      return AccountUpgradeResult.Failed(retry.errorMessage)
    }
    val fresh = retry.toCredential()
      ?: return AccountUpgradeResult.Failed("Could not read the Apple credential")

    return signInToExistingAccount(fresh)
  }

  override suspend fun signInToExistingAccount(credential: AuthCredential): AccountUpgradeResult {
    return try {
      val result = authProvider.signInWithCredential(credential)
      val user = result.user ?: authProvider.currentUser
      ?: return AccountUpgradeResult.Failed("Sign-in returned no user")
      if (user.isAnonymous) {
        return AccountUpgradeResult.Failed("Sign-in did not switch to the permanent account")
      }
      AccountUpgradeResult.Linked(user)
    } catch (e: Exception) {
      logger.e(e) { "Sign-in to existing account failed" }
      AccountUpgradeResult.Failed(e.message ?: "Sign-in failed")
    }
  }

  override suspend fun updateDisplayName(name: String) {
    val user = authProvider.currentUser ?: return
    if (name.isBlank() || name == user.displayName) return
    try {
      user.updateProfile(displayName = name, photoUrl = user.photoURL)
      user.reload()
    } catch (e: Exception) {
      // Best-effort: the in-app name is already correct everywhere the client reads it. This only
      // keeps the token in step for the server-side reads.
      logger.w(e) { "Could not push display name to the auth profile" }
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
