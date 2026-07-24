package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.InviteLink
import dev.fanfly.wingslog.feature.sharing.model.InvitePreview
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

  /**
   * Whether [acId] is hosted by *another* account — shared into this user's fleet, so the host, not
   * this user, owns and pays for its storage. Answered locally from the refs store (a ref exists iff
   * the aircraft was shared in), so it is instant and offline-correct; false for an own aircraft and
   * while signed out.
   *
   * This is what makes the attachment gate aircraft-scoped (design §9.7): on a foreign host's
   * aircraft the member is never blocked by their *own* subscription — the host's entitlement governs
   * and the broker enforces it — so an unsubscribed technician can still upload on a paid owner's plane.
   */
  fun observeIsForeignHosted(acId: String): Flow<Boolean>

  /**
   * Whether this aircraft is part of a share at all — true for *every* partner in it, the hosting
   * owner included, false for an aircraft nobody else can see.
   *
   * This is what gates anything that only makes sense when more than one person can write: the
   * shared marker, and the authorship attestations on logs. On an unshared aircraft the only author
   * there has ever been is the user themselves, so attesting to it says nothing.
   *
   * Answered from two sources because neither covers everyone: a ref means it was shared *into* our
   * fleet (false for the host, but local, so it holds offline), while a roster of more than one
   * covers the host (but is an online-only listener, so it goes quiet offline — it starts `false`
   * rather than stalling whatever combines it).
   */
  fun observeIsShared(acId: String): Flow<Boolean>

  /**
   * Mints a pairing code (#164). Server-side: the code doc lives in a collection no client may read,
   * which is what keeps an aircraft id out of the invitee's hands (#202/#204).
   *
   * [aircraftLabel] is display-only, shown to the invitee before they accept (#201) — the server
   * cannot read it out of the aircraft record, which is opaque proto bytes.
   */
  suspend fun createInvite(
    acId: String,
    role: ShareRole,
    aircraftLabel: String,
  ): Result<InviteLink>

  /** Destroys the code. A cancelled invite and a never-existed one become the same state. */
  suspend fun cancelInvite(acId: String, codeId: String): Result<Unit>

  /**
   * Joins by code alone — no aircraft id, no host uid. The invitee never holds either, which is the
   * whole point: those are what an attacker needed to fabricate a same-id aircraft (#202/#204).
   */
  suspend fun redeemInvite(code: String): Result<RedeemOutcome>

  /**
   * Resolves a code to what the invitee needs to decide — aircraft label, inviting owner, role — and
   * grants nothing (#201). Impossible before #164: an invitee held a real aircraft id, and the rules
   * (correctly) refuse to resolve one for a non-member, so accepting an invite meant accepting blind.
   */
  suspend fun previewInvite(code: String): Result<InvitePreview>

  suspend fun revokeMember(acId: String, uid: String): Result<Unit>

  suspend fun updateRole(acId: String, uid: String, role: ShareRole): Result<Unit>

  suspend fun leave(acId: String): Result<Unit>

  /**
   * Publishes the caller's display fields + self-technician mirror to every share they belong to
   * (§7.1/§7.2). Call on redeem, on self-technician edit, and at app start — it is idempotent, so
   * the app-start call doubles as the retry for a publish that failed offline. Best-effort: a
   * failure is logged, not surfaced.
   *
   * Membership is read from the *local* stores, so a share joined moments ago is not in it yet — its
   * `SharedAircraftRef` is still in flight. Pass [alsoPublishTo] with that aircraft id (the redeem
   * path does) to publish into it anyway; without it the member doc keeps the auth-token name the
   * redeem function seeded until the next app start.
   */
  suspend fun publishTechnicianMirror(alsoPublishTo: String? = null): Result<Unit>

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
