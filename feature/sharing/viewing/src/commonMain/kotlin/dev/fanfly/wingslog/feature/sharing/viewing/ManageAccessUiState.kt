package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import wingslog.core.sharedassets.generated.resources.cancel

/** Plain UI state for [ManageAccessScreen]; produced by the host-side ManageAccessViewModel. */
data class ManageAccessUiState(
  val isLoading: Boolean = true,
  /** The signed-in user's role on this thing; `OWNER` may manage access, others are read-only. */
  val myRole: ShareRole? = null,
  val members: List<ShareMember> = emptyList(),
  val error: String? = null,
  /** Set once the user has left the share, so the host can pop back to the fleet. */
  val leaveSuccess: Boolean = false,
  /**
   * Set when the owner revoked this user's access while they had the screen open. Same exit as
   * [leaveSuccess] — they are no longer a member, so the roster on screen is a lie.
   */
  val accessRevoked: Boolean = false,
  /**
   * Cloud Sync is on. Sharing is a cloud feature end to end (PRD E2) — with sync off there is
   * nothing to share into and nothing to receive from, so management is disabled and explains
   * itself rather than failing on tap.
   */
  val syncEnabled: Boolean = true,
  /**
   * Hosting a share is a Pro capability (subscription gate). When off, the owner's "Create invite
   * code" action is surfaced as a promo (opens the upsell) rather than hidden; managing/leaving an
   * existing share is unaffected. `true` while the capability is off (default-open). See
   * subscription_design.html §6.
   */
  val canHostShare: Boolean = true,
  /** Which of the four steps (people → role → code → member) the panel is showing. */
  val view: AccessPanelView = AccessPanelView.MAIN,
  /** e.g. "N7245K · Cessna 172S", carried on new invites for the invitee's preview (#201). */
  val thingLabel: String = "",
  val invites: List<PendingInvite> = emptyList(),
  val selectedInviteRole: ShareRole = ShareRole.TECHNICIAN,
  val creatingInvite: Boolean = false,
  /** True while a cancel request for the active invite is in flight — disables the button so the
   *  slow round trip can't be tapped again, instead of failing silently on the second call. */
  val cancellingInvite: Boolean = false,
  /** codeId of the invite the CODE view is showing. */
  val activeInviteCodeId: String? = null,
  /** uid of the member the MEMBER view is showing. */
  val activeMemberUid: String? = null,
  val helpExpanded: Boolean = false,
  /** Transient confirmation, cleared a moment after it's shown. */
  val toast: AccessToast? = null,
) {
  /** Owners manage access; everyone else sees a read-only roster. Never while sync is off. */
  val canManage: Boolean get() = myRole == ShareRole.OWNER && syncEnabled

  /** A non-host member may leave; the host tears the share down by deleting the thing instead. */
  val canLeave: Boolean get() = syncEnabled && members.any { it.isSelf && !it.isHost }

  val activeInvite: PendingInvite? get() = invites.firstOrNull { it.codeId == activeInviteCodeId }
  val activeMember: ShareMember? get() = members.firstOrNull { it.uid == activeMemberUid }
}
