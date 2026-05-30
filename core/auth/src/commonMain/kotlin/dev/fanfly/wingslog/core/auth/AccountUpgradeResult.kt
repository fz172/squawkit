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
  data class CredentialInUse(val credential: AuthCredential) : AccountUpgradeResult

  /** The user dismissed the provider sheet. No change. */
  data object Cancelled : AccountUpgradeResult

  /** Something went wrong; [message] is safe to surface or log. */
  data class Failed(val message: String) : AccountUpgradeResult
}
