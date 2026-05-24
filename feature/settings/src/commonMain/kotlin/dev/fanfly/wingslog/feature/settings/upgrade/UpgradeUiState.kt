package dev.fanfly.wingslog.feature.settings.upgrade

import dev.gitlive.firebase.auth.AuthCredential

/**
 * Drives the guest → account upgrade flow surfaced from the Settings profile area.
 * See docs/account_upgrade_design.html §8.
 */
sealed interface UpgradeUiState {
  /** Nothing in flight; the CTA is shown for anonymous users when the flag is on. */
  data object Idle : UpgradeUiState

  /** Provider sign-in or sync re-keying is running; show a blocking progress indicator. */
  data object Working : UpgradeUiState

  /** The chosen account already exists; confirm before merging this device's records into it. */
  data class ConfirmMerge(val credential: AuthCredential) : UpgradeUiState

  /** Done. Rendered as a calm "Syncing complete" with no record count. */
  data object Success : UpgradeUiState

  /** Something failed; [message] is safe to surface. The user can retry. */
  data class Error(val message: String) : UpgradeUiState
}
