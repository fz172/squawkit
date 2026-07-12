package dev.fanfly.wingslog.feature.sharing.model

/**
 * Client-side view models for aircraft sharing. Scaffolded stubs — fields are refined as the
 * membership plumbing (P2) and UI (P4/P5) land. The role here is the UI/permission role; it mirrors
 * the ACL and the synced `SharedAircraftRef.role`. See docs/sharing §6.
 */
enum class ShareRole {
  OWNER,
  TECHNICIAN,
}

/** A member of a shared aircraft, as surfaced in Manage Access. */
data class ShareMember(
  val uid: String,
  val displayName: String,
  val role: ShareRole,
  /** Account photo, published by the member's own client alongside the mirror. Null → initials. */
  val photoUrl: String? = null,
  val isHost: Boolean = false,
  val isSelf: Boolean = false,
)

/** An outstanding invite shown to owners in Manage Access. */
data class PendingInvite(
  val tokenHash: String,
  val role: ShareRole,
  val createdAtEpochMs: Long,
  val expiresAtEpochMs: Long,
  /**
   * The share URL, recovered from a **device-local** cache. The secret is never stored server-side
   * (only its hash), so this is non-null only for invites minted on this device — enough to re-show
   * the QR/link. Null for an invite created elsewhere (or after a reinstall): cancel + re-create.
   */
  val url: String? = null,
)

/** Snapshot of a share's members and pending invites (Firestore-backed, online-only). */
data class AircraftShareState(
  val members: List<ShareMember> = emptyList(),
  val invites: List<PendingInvite> = emptyList(),
)

/** A freshly minted invite: the share URL to hand off, plus the token hash for later cancellation. */
data class InviteLink(
  val url: String,
  val tokenHash: String,
)

/** Result of redeeming an invite. */
data class RedeemOutcome(
  val aircraftId: String,
  val hostUid: String,
  val role: ShareRole,
  val alreadyMember: Boolean = false,
)
