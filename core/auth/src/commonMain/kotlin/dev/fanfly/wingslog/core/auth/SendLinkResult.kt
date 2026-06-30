package dev.fanfly.wingslog.core.auth

/**
 * Outcome of requesting a passwordless email sign-in link (leg 1 of the flow). Distinguishes a
 * client-side validation problem ([InvalidEmail]) from a backend/network failure ([Failed]) so the
 * UI can react appropriately. See docs/account/email_link_signin_design.html.
 */
sealed interface SendLinkResult {
  /** The link was sent to [email]; the UI should show the "check your inbox" state. */
  data class Sent(val email: String) : SendLinkResult

  /** The address failed basic format validation; nothing was sent. */
  data object InvalidEmail : SendLinkResult

  /** Sending failed (offline, rate-limited, etc.). [message] is best-effort detail for logging. */
  data class Failed(val message: String) : SendLinkResult
}
