package dev.fanfly.wingslog.feature.sharing.viewing.redeem

import dev.fanfly.wingslog.feature.sharing.model.InvitePreview
import dev.fanfly.wingslog.feature.sharing.model.ShareRole

/**
 * State of the thing-invite redemption surface. A non-member can't read the thing before
 * joining (rules deny it, and the share URL carries only id + secret), so the confirm step is
 * intentionally detail-light; the offered role is surfaced on success from the function's response.
 */
sealed interface RedeemUiState {
  data object Hidden : RedeemUiState

  /**
   * What you are about to join (#201). Resolved from the code by the server — the invitee holds no
   * thing id, and the rules would (rightly) refuse to resolve one for a non-member.
   *
   * [preview] is null while it is still loading, or if the lookup failed: the sheet then says less
   * rather than blocking Accept on a call that is only there to inform.
   */
  data class Confirm(val preview: InvitePreview? = null) : RedeemUiState

  /** Signed out / guest: the invite stays parked until the user signs in with a real account. */
  data object NeedsSignIn : RedeemUiState
  data object Redeeming : RedeemUiState
  data class Success(val role: ShareRole) : RedeemUiState
  data object AlreadyMember : RedeemUiState
  data class Failed(val message: String?) : RedeemUiState
}
