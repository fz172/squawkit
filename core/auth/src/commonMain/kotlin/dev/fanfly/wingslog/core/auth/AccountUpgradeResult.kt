package dev.fanfly.wingslog.core.auth

import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseUser

/**
 * Outcome of upgrading an anonymous (guest) session to a permanent Google/Apple account.
 *
 * See docs/account/account_upgrade_design.html. The clean path links the provider to the existing
 * anonymous user (UID preserved → local data needs no migration). A collision means the chosen
 * account already exists, so the caller offers the merge path via [AuthManager.signInToExistingAccount].
 */
sealed interface AccountUpgradeResult {
  /** Linked (or signed in), now permanent. [user] is the resulting Firebase user. */
  data class Linked(val user: FirebaseUser) : AccountUpgradeResult

  /** The provider credential already belongs to another account; offer to merge into it. */
  data class CredentialInUse(val credential: AuthCredential) :
    AccountUpgradeResult

  /**
   * Same collision as [CredentialInUse], but the credential cannot be replayed, so merging needs a
   * fresh authorization from [provider].
   *
   * Sign in with Apple is the case: its identity token is single-use and bound to the nonce it was
   * issued for, so the token the failed link consumed is rejected with
   * `ERROR_MISSING_OR_INVALID_NONCE`. Google's can back a second credential, which is why that path
   * returns [CredentialInUse] instead.
   *
   * Kept distinct so the *caller* decides when to re-prompt: the user is told their account already
   * exists before a second provider sheet appears, rather than being shown one twice with no
   * explanation.
   */
  data class ReauthRequiredToMerge(val provider: AuthProvider) : AccountUpgradeResult

  /** The user dismissed the provider sheet. No change. */
  data object Cancelled : AccountUpgradeResult

  /** Something went wrong; [message] is safe to surface or log. */
  data class Failed(val message: String) : AccountUpgradeResult
}
