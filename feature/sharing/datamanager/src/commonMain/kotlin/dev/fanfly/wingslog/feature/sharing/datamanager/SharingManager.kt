package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.InviteLink
import dev.fanfly.wingslog.feature.sharing.model.RedeemOutcome
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import kotlinx.coroutines.flow.Flow

/**
 * Manages aircraft-share access: members, invites, roles, and the technician mirror.
 *
 * A deliberate exception to "managers never touch Firestore" — share ACL docs are plain-field,
 * rules-inspected, and function-written, so they can't ride the entity-sync path. This manager
 * therefore talks to Firestore/Functions directly, an online-only surface (precedent:
 * ExportHistoryRemoteRepository). Shared *content* remains fully local-first. See docs/sharing §6.2.
 *
 * Scaffold: signatures per the design; implementations land with the membership plumbing (P2, #114–120).
 */
interface SharingManager {
  /** Members + pending invites for [acId], from Firestore snapshots (online-only). */
  fun observeShareState(acId: String): Flow<AircraftShareState>

  /** The caller's role on [acId], resolved locally (refs store / own aircraft) — instant, offline-correct. */
  fun observeMyRole(acId: String): Flow<ShareRole?>

  suspend fun createInvite(acId: String, role: ShareRole): Result<InviteLink>

  suspend fun cancelInvite(acId: String, tokenHash: String): Result<Unit>

  suspend fun redeemInvite(acId: String, secret: String): Result<RedeemOutcome>

  suspend fun revokeMember(acId: String, uid: String): Result<Unit>

  suspend fun updateRole(acId: String, uid: String, role: ShareRole): Result<Unit>

  suspend fun leave(acId: String): Result<Unit>

  /**
   * Publishes the caller's display fields + self-technician mirror to every share they belong to
   * (§7.1/§7.2). Call on redeem, on self-technician edit, and at app start — it is idempotent, so
   * the app-start call doubles as the retry for a publish that failed offline. Best-effort: a
   * failure is logged, not surfaced.
   */
  suspend fun publishTechnicianMirror(): Result<Unit>

  /**
   * Every *other* member, across all of the user's shares, who has published a technician mirror —
   * as a [Technician] snapshot stamped with their uid in `source_uid`. Membership-with-mirror is the
   * criterion, not role, so an owner-mechanic appears too (§7.3). Deduped across shares.
   *
   * Online-only, like the rest of the share surface: it degrades to an empty list, never an error.
   */
  fun observeLinkedTechnicians(): Flow<List<Technician>>

  /**
   * The same, scoped to one aircraft: the members of *this* share who have published a mirror. This
   * is what the log-form picker lists — you sign a log for a specific aircraft, so only that
   * aircraft's members are selectable (§7.3).
   */
  fun observeLinkedTechnicians(acId: String): Flow<List<Technician>>
}
