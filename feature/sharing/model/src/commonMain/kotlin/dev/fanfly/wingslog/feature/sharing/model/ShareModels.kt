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
  /** SHA-256 of the code. The code itself is not stored server-side and cannot be recovered. */
  val codeId: String,
  val role: ShareRole,
  val createdAtEpochMs: Long,
  val expiresAtEpochMs: Long,
  /**
   * The code, from a **device-local** cache — non-null only for an invite minted on this device, so
   * the owner can re-show it. Null for one created elsewhere or after a reinstall: cancel and mint a
   * new one. The server keeps only the hash, so nothing can recover it (#164).
   */
  val code: String? = null,
)

/** Snapshot of a share's members and pending invites (Firestore-backed, online-only). */
data class AircraftShareState(
  val members: List<ShareMember> = emptyList(),
  val invites: List<PendingInvite> = emptyList(),
  /**
   * The rules refused us the roster. Two very different situations produce this, and only the caller
   * can tell them apart: an owner whose aircraft has no share doc yet (normal — nothing to read),
   * versus a member who was just revoked (their read is denied the moment they leave `memberRoles`).
   * If you have already seen yourself in this roster, this is a revocation.
   */
  val accessDenied: Boolean = false,
)

/** A freshly minted invite: the share URL to hand off, plus the token hash for later cancellation. */
/**
 * A freshly minted pairing-code invite (#164). Returned exactly once, at creation — the code is
 * never stored anywhere a client can read it back, not even the owner's.
 */
data class InviteLink(
  /** `EFA1GGTH` — what the server stores and redeems. */
  val code: String,
  /** `EFA1-GGTH` — what a human reads aloud or types. */
  val formattedCode: String,
  /** SHA-256 of the code: what the owner sees in the pending list, and cancels by. */
  val codeId: String,
  /** `https://…/share#EFA1GGTH` — names no aircraft and no host, only the opaque code. */
  val url: String,
  val expiresAtEpochMs: Long,
)

/** What an invitee is shown before accepting (#201), resolved from the code alone. */
data class InvitePreview(
  /** e.g. "N2037O · Cessna 172". Empty if the inviting client did not supply one. */
  val aircraftLabel: String,
  val hostName: String,
  val role: ShareRole,
)

/** Result of redeeming an invite. */
data class RedeemOutcome(
  val aircraftId: String,
  val hostUid: String,
  val role: ShareRole,
  val alreadyMember: Boolean = false,
)
