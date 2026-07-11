package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.model.sharing.ShareRole as ProtoShareRole
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.blob.sha256Hex
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.InviteLink
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.RedeemOutcome
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Online-only Firestore/Functions client for aircraft sharing (docs/sharing §6.2). Reads of the
 * share ACL and invite writes go straight to Firestore; every cross-tree operation is a callable.
 * `observeMyRole` is answered locally from the refs store so it's instant and offline-correct.
 */
class SharingManagerImpl(
  private val auth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
  storeFactory: EntityStoreFactory,
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock,
) : SharingManager {

  private val functions = Firebase.functions(FUNCTIONS_REGION)
  private val refStore = storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
  private val aircraftStore = storeFactory.create<Aircraft>(CollectionKind.Aircraft)

  private fun shareDoc(acId: String) = firestore.collection(SHARES).document(acId)

  override fun observeShareState(acId: String): Flow<AircraftShareState> {
    val myUid = auth.currentUser?.uid
    val root = shareDoc(acId).snapshots.map { it.takeIf { s -> s.exists }?.data<RootWire>() }
    val members = shareDoc(acId).collection(MEMBERS).snapshots
    val invites = shareDoc(acId).collection(INVITES).snapshots
    return combine(root, members, invites) { rootDoc, memberSnaps, inviteSnaps ->
      val hostUid = rootDoc?.hostUid
      AircraftShareState(
        members = memberSnaps.documents.map { doc ->
          val m = doc.data<MemberWire>()
          ShareMember(
            uid = doc.id,
            displayName = m.displayName,
            role = m.role.toModel(),
            isHost = doc.id == hostUid,
            isSelf = doc.id == myUid,
          )
        },
        invites = inviteSnaps.documents
          .map { it.id to it.data<InviteWire>() }
          .filter { (_, i) -> !i.revoked && i.useCount < i.maxUses }
          .map { (tokenHash, i) ->
            PendingInvite(
              tokenHash = tokenHash,
              role = i.role.toModel(),
              createdAtEpochMs = i.createdAt.toMillisLong(),
              expiresAtEpochMs = i.expiresAt.toMillisLong(),
            )
          },
      )
    }
      // Recover each invite's share URL from the device-local cache (the secret isn't in Firestore),
      // so a returning owner can re-show the QR/link. map's transform is suspend, so the DB read fits.
      .map { state ->
        state.copy(invites = state.invites.map { it.copy(url = localInviteUrl(it.tokenHash)) })
      }
  }

  override fun observeMyRole(acId: String): Flow<ShareRole?> {
    val uid = auth.currentUser?.uid ?: return flowOf(null)
    val scope = EntityScope.userRoot(uid)
    // Own aircraft ⇒ owner; otherwise the ref's advisory role; otherwise not a member.
    return combine(
      aircraftStore.observe(acId, scope),
      refStore.observe(acId, scope),
    ) { own, ref ->
      when {
        own != null -> ShareRole.OWNER
        ref != null -> ref.value.role.toModel()
        else -> null
      }
    }
  }

  @OptIn(ExperimentalEncodingApi::class)
  override suspend fun createInvite(acId: String, role: ShareRole): Result<InviteLink> = runCatching {
    // Weak-random is intentional for now: the pairing-code rework (#164) replaces this mechanism
    // (short human code + rate-limited redeem), which subsumes the CSPRNG concern for this secret.
    val secret = Base64.UrlSafe.encode(Random.nextBytes(16)).trimEnd('=')
    val tokenHash = sha256Hex(secret.encodeToByteArray())
    val uid = requireUid()
    val now = Timestamp.now()
    val url = "$SHARE_URL_BASE#$acId.$secret"
    // Cache the link locally BEFORE writing the invite doc: that write fires Firestore's optimistic
    // snapshot, which makes observeShareState re-read the cache — so the URL must already be there,
    // or the just-created invite would show as "created elsewhere". (The secret is never persisted
    // server-side, §3.1; this local cache is the only way to re-show the QR/link.)
    writeLock.withLock { db.schemaQueries.upsertConfig(uid, inviteUrlKey(tokenHash), url) }
    // Lazy-create the ACL doc so the owner is in memberRoles before the invite write — the invite
    // rule (isShareOwner) reads it. First share of an aircraft bootstraps this (docs/sharing §3.1);
    // subsequent memberRoles changes are function-only. See §2.1.
    ensureShareRoot(acId, uid)
    shareDoc(acId).collection(INVITES).document(tokenHash).set(
      InviteWire(
        role = role.wire(),
        createdBy = uid,
        createdAt = now,
        expiresAt = Timestamp(now.seconds + INVITE_TTL_SECONDS, now.nanoseconds),
        maxUses = 1,
        useCount = 0,
        revoked = false,
      ),
    )
    InviteLink(url = url, tokenHash = tokenHash)
  }

  override suspend fun cancelInvite(acId: String, tokenHash: String): Result<Unit> = runCatching {
    shareDoc(acId).collection(INVITES).document(tokenHash).update("revoked" to true)
  }

  override suspend fun redeemInvite(acId: String, secret: String): Result<RedeemOutcome> = runCatching {
    val res = functions.httpsCallable("redeemAircraftShareInvite")
      .invoke(RedeemRequest(aircraftId = acId, secret = secret))
      .data<RedeemResponse>()
    RedeemOutcome(
      aircraftId = res.aircraftId,
      hostUid = res.hostUid,
      role = res.role.toModel(),
      alreadyMember = res.alreadyMember,
    )
  }

  override suspend fun revokeMember(acId: String, uid: String): Result<Unit> = runCatching {
    functions.httpsCallable("revokeAircraftShare")
      .invoke(RevokeRequest(aircraftId = acId, memberUid = uid))
  }

  override suspend fun updateRole(acId: String, uid: String, role: ShareRole): Result<Unit> = runCatching {
    functions.httpsCallable("updateAircraftShareRole")
      .invoke(UpdateRoleRequest(aircraftId = acId, memberUid = uid, role = role.wire()))
  }

  override suspend fun leave(acId: String): Result<Unit> = revokeMember(acId, requireUid())

  override suspend fun publishTechnicianMirror(): Result<Unit> =
    Result.success(Unit) // TODO(P5, #135): publish the self-technician mirror to each membership.

  /** The device-local share URL for [tokenHash], if this device minted the invite; else null. */
  private suspend fun localInviteUrl(tokenHash: String): String? {
    val uid = auth.currentUser?.uid ?: return null
    return db.schemaQueries.selectConfig(uid, inviteUrlKey(tokenHash)).awaitAsOneOrNull()
  }

  private fun inviteUrlKey(tokenHash: String) = "$INVITE_URL_KEY_PREFIX$tokenHash"

  private fun requireUid(): String =
    auth.currentUser?.uid ?: error("Sharing requires a signed-in user")

  /**
   * Bootstrap the `aircraft_shares/{acId}` ACL doc on first share (docs/sharing §2.1/§3.1): the
   * owner writes themselves into `memberRoles` so the subsequent invite write passes `isShareOwner`.
   * No-op once the doc exists (further `memberRoles` changes are function-only). Rules gate the
   * create to the aircraft's own owner.
   */
  private suspend fun ensureShareRoot(acId: String, uid: String) {
    if (shareDoc(acId).get().exists) return
    shareDoc(acId).set(
      ShareRootCreateWire(
        hostUid = uid,
        memberRoles = mapOf(uid to ShareRole.OWNER.wire()),
      ),
    )
  }

  companion object {
    private const val FUNCTIONS_REGION = "us-central1"
    private const val SHARES = "aircraft_shares"
    private const val MEMBERS = "members"
    private const val INVITES = "invites"
    // Matches the App Link / Universal Link host verified for deep linking (see AndroidManifest and
    // AircraftShareDeepLinks) so the link opens the app; the web app serves the same URL as a fallback.
    private const val SHARE_URL_BASE = "https://squawkit.fanfly.dev/share"
    private const val INVITE_TTL_SECONDS = 7L * 24 * 60 * 60
    private const val INVITE_URL_KEY_PREFIX = "share_invite_url:"
  }
}

private fun ShareRole.wire(): String = if (this == ShareRole.OWNER) "owner" else "technician"
private fun String.toModel(): ShareRole = if (this == "owner") ShareRole.OWNER else ShareRole.TECHNICIAN
private fun ProtoShareRole.toModel(): ShareRole =
  if (this == ProtoShareRole.SHARE_ROLE_OWNER) ShareRole.OWNER else ShareRole.TECHNICIAN
private fun Timestamp.toMillisLong(): Long = toMilliseconds().toLong()

@Serializable private data class RootWire(val hostUid: String = "")
/** Owner-bootstrapped ACL doc: hostUid + the owner's own memberRoles entry (§3.1). */
@Serializable private data class ShareRootCreateWire(
  val hostUid: String,
  val memberRoles: Map<String, String>,
)
@Serializable private data class MemberWire(val role: String = "technician", val displayName: String = "")
@Serializable private data class InviteWire(
  val role: String = "technician",
  val createdBy: String = "",
  val createdAt: Timestamp = Timestamp.now(),
  val expiresAt: Timestamp = Timestamp.now(),
  val maxUses: Int = 1,
  val useCount: Int = 0,
  val revoked: Boolean = false,
)

@Serializable private data class RedeemRequest(val aircraftId: String, val secret: String)
@Serializable private data class RedeemResponse(
  val aircraftId: String = "",
  val hostUid: String = "",
  val role: String = "technician",
  val alreadyMember: Boolean = false,
)
@Serializable private data class RevokeRequest(val aircraftId: String, val memberUid: String)
@Serializable private data class UpdateRoleRequest(
  val aircraftId: String,
  val memberUid: String,
  val role: String,
)
