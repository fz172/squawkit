package dev.fanfly.wingslog.feature.login.upgrade

import dev.fanfly.wingslog.core.auth.AuthProvider

/**
 * Drives the guest → account upgrade flow surfaced from the Settings profile area.
 * See docs/account/account_upgrade_design.html §8.
 *
 * Google and Apple run [ChoosingProvider] → [Working] → [Success]: the provider sheet is native and
 * returns in one call. Email cannot, because leg 1 leaves the app behind — it runs
 * [ChoosingProvider] → [EnteringEmail] → [LinkSent], and then resumes at [ConfirmLink] whenever the
 * link reopens the app, which may be minutes later or never.
 */
sealed interface UpgradeUiState {
  /** Nothing in flight; the CTA is shown for anonymous users. */
  data object Idle : UpgradeUiState

  /** The provider picker is open. [providers] is what this platform offers, in display order. */
  data class ChoosingProvider(val providers: List<AuthProvider>) : UpgradeUiState

  /**
   * Collecting the address for an email-link upgrade. [email] lives here rather than in composable
   * `remember` so it survives the recomposition and teardown a keyboard or rotation can cause.
   * [error] is set for a malformed address or a send failure.
   */
  data class EnteringEmail(
    val email: String = "",
    val error: String? = null,
    val sending: Boolean = false,
  ) : UpgradeUiState

  /** Leg 1 done — the link is in [email]'s inbox and the app is waiting to be reopened by it. */
  data class LinkSent(val email: String) : UpgradeUiState

  /**
   * The link came back and we are asking before doing anything irreversible.
   *
   * Deliberately not automatic: linking attaches the address to *this* device's guest data, and a
   * link opened against a different guest session than the one that requested it would silently
   * bind someone's records to the wrong account. [email] is what the link was requested for.
   */
  data class ConfirmLink(val email: String, val link: String) : UpgradeUiState

  /** Provider sign-in or sync re-keying is running; show a blocking progress indicator. */
  data object Working : UpgradeUiState

  /** Done. Rendered as a calm "Syncing complete" with no record count. */
  data object Success : UpgradeUiState

  /** Something failed; [message] is safe to surface. The user can retry. */
  data class Error(val message: String) : UpgradeUiState
}
