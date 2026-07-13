package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
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
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import dev.fanfly.wingslog.core.model.sharing.ShareRole as ProtoShareRole

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
  private val technicianManager: TechnicianManager,
) : SharingManager {

  private val functions = Firebase.functions(FUNCTIONS_REGION)
  private val refStore =
    storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
  private val aircraftStore =
    storeFactory.create<Aircraft>(CollectionKind.Aircraft)

  private fun shareDoc(acId: String) = firestore.collection(SHARES)
    .document(acId)

  override fun observeShareState(acId: String): Flow<AircraftShareState> {
    val myUid = auth.currentUser?.uid
    val root = shareDoc(acId).snapshots.map {
      it.takeIf { s -> s.exists }
        ?.data<RootWire>()
    }
    val members = shareDoc(acId).collection(MEMBERS).snapshots
    // Invites are owner-only in the rules, so a technician's listener is denied. Degrade to "no
    // pending invites" rather than letting that failure take down the combine — the roster IS
    // readable by any member, and it's what backs their read-only view and the Leave action.
    val invites: Flow<List<PendingInvite>> =
      shareDoc(acId).collection(INVITES).snapshots
        .map { inviteSnaps ->
          inviteSnaps.documents
            .map { it.id to it.data<InviteWire>() }
            .filter { (_, i) -> !i.revoked && i.useCount < i.maxUses }
            .map { (tokenHash, i) ->
              PendingInvite(
                tokenHash = tokenHash,
                role = i.role.toModel(),
                createdAtEpochMs = i.createdAt.toMillisLong(),
                expiresAtEpochMs = i.expiresAt.toMillisLong(),
              )
            }
        }
        .catch { emit(emptyList()) }
    return combine(
      root,
      members,
      invites
    ) { rootDoc, memberSnaps, pendingInvites ->
      val hostUid = rootDoc?.hostUid
      val memberRoles = rootDoc?.memberRoles.orEmpty()
      val docs =
        memberSnaps.documents.associate { it.id to it.data<MemberWire>() }
      // memberRoles on the ACL root is the authoritative membership list; the members subcollection
      // only carries display detail. Drive the roster from the former so a member with no doc yet
      // still appears — notably the hosting owner, who never redeems and so is never written by a
      // function. Union with the docs so a doc that outlives its ACL entry isn't silently dropped.
      AircraftShareState(
        members = (memberRoles.keys + docs.keys)
          .map { uid ->
            val m = docs[uid]
            ShareMember(
              uid = uid,
              displayName = m?.displayName.orEmpty(),
              role = (m?.role ?: memberRoles[uid]).orEmpty()
                .toModel(),
              photoUrl = m?.photoUrl,
              isHost = uid == hostUid,
              isSelf = uid == myUid,
            )
          }
          // Host first, then everyone else — the owner is the anchor of the roster.
          .sortedByDescending { it.isHost },
        invites = pendingInvites,
      )
    }
      // Recover each invite's share URL from the device-local cache (the secret isn't in Firestore),
      // so a returning owner can re-show the QR/link. map's transform is suspend, so the DB read fits.
      .map { state ->
        state.copy(invites = state.invites.map { it.copy(url = localInviteUrl(it.tokenHash)) })
      }
      // A revoked member's roster listener is denied the instant they leave `memberRoles` — well
      // before the ref tombstone reaches their device. Surface that as state rather than an error:
      // it is the earliest signal a member has that their access ended, and the screen watching this
      // roster has to react to it. Rules don't flap, so a denial is a real answer, not a hiccup.
      .catch { e ->
        if (isPermissionDenied(e)) emit(AircraftShareState(accessDenied = true)) else throw e
      }
  }

  /** True for a Firestore `PERMISSION_DENIED`; every other failure is someone else's problem. */
  private fun isPermissionDenied(e: Throwable): Boolean =
    e is FirebaseFirestoreException && e.code == FirestoreExceptionCode.PERMISSION_DENIED

  override fun observeIsShared(acId: String): Flow<Boolean> {
    val uid = auth.currentUser?.uid ?: return flowOf(false)
    val sharedIntoMyFleet = refStore.observe(acId, EntityScope.userRoot(uid))
      .map { it != null }
    val hasPartners = observeShareState(acId)
      .map { it.members.size > 1 }
      // The roster is an online-only listener. Seed it so a combine of this flow produces a value
      // immediately instead of hanging until Firestore answers — offline it never would, and the
      // screen behind it would sit on a spinner forever.
      .onStart { emit(false) }
      .catch { emit(false) }
    return combine(sharedIntoMyFleet, hasPartners) { intoMyFleet, partnered ->
      intoMyFleet || partnered
    }.distinctUntilChanged()
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
  override suspend fun createInvite(
    acId: String,
    role: ShareRole
  ): Result<InviteLink> = runCatching {
    // Weak-random is intentional for now: the pairing-code rework (#164) replaces this mechanism
    // (short human code + rate-limited redeem), which subsumes the CSPRNG concern for this secret.
    val secret = Base64.UrlSafe.encode(Random.nextBytes(16))
      .trimEnd('=')
    val tokenHash = sha256Hex(secret.encodeToByteArray())
    val uid = requireUid()
    val now = Timestamp.now()
    val url = "$SHARE_URL_BASE#$acId.$secret"
    // Cache the link locally BEFORE writing the invite doc: that write fires Firestore's optimistic
    // snapshot, which makes observeShareState re-read the cache — so the URL must already be there,
    // or the just-created invite would show as "created elsewhere". (The secret is never persisted
    // server-side, §3.1; this local cache is the only way to re-show the QR/link.)
    writeLock.withLock {
      db.schemaQueries.upsertConfig(
        uid,
        inviteUrlKey(tokenHash),
        url
      )
    }
    // Lazy-create the ACL doc so the owner is in memberRoles before the invite write — the invite
    // rule (isShareOwner) reads it. First share of an aircraft bootstraps this (docs/sharing §3.1);
    // subsequent memberRoles changes are function-only. See §2.1.
    ensureShareRoot(acId, uid)
    shareDoc(acId).collection(INVITES)
      .document(tokenHash)
      .set(
        InviteWire(
          role = role.wire(),
          createdBy = uid,
          createdAt = now,
          expiresAt = Timestamp(
            now.seconds + INVITE_TTL_SECONDS,
            now.nanoseconds
          ),
          maxUses = 1,
          useCount = 0,
          revoked = false,
        ),
      )
    InviteLink(url = url, tokenHash = tokenHash)
  }

  override suspend fun cancelInvite(
    acId: String,
    tokenHash: String
  ): Result<Unit> = runCatching {
    shareDoc(acId).collection(INVITES)
      .document(tokenHash)
      .update("revoked" to true)
  }

  override suspend fun redeemInvite(
    acId: String,
    secret: String
  ): Result<RedeemOutcome> = runCatching {
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

  override suspend fun revokeMember(acId: String, uid: String): Result<Unit> =
    runCatching {
      functions.httpsCallable("revokeAircraftShare")
        .invoke(RevokeRequest(aircraftId = acId, memberUid = uid))
    }

  override suspend fun updateRole(
    acId: String,
    uid: String,
    role: ShareRole
  ): Result<Unit> = runCatching {
    functions.httpsCallable("updateAircraftShareRole")
      .invoke(
        UpdateRoleRequest(
          aircraftId = acId,
          memberUid = uid,
          role = role.wire()
        )
      )
  }

  override suspend fun leave(acId: String): Result<Unit> =
    revokeMember(acId, requireUid())

  /**
   * Publishes the caller's display fields + self-technician mirror into their member doc on every
   * share they belong to (design §7.1/§7.2). Every member publishes, Owner or Technician alike —
   * the picker lists membership-with-mirror, not role.
   *
   * Idempotent, and cheap enough to run on every app start — which is what makes it the retry for a
   * publish that couldn't land (offline, or killed mid-write) and the self-heal for a member doc
   * still carrying a name from before this existed. Best-effort by design: freshness is eventual,
   * and log snapshots capture whatever is current at signing time.
   */
  override suspend fun publishTechnicianMirror(alsoPublishTo: String?): Result<Unit> = runCatching {
    val user = auth.currentUser ?: return@runCatching
    if (user.isAnonymous) return@runCatching
    val uid = user.uid

    val self = technicianManager.observeSelf()
      .first()
    // The in-app profile name wins over the Firebase Auth account name. The redeem function seeds
    // displayName from the auth token because it has nothing else to go on, but the account name
    // (e.g. from Google) is not what the user edits or expects to see — the self-technician record
    // is. Same precedence the shell uses for the account row.
    val update = MemberSelfUpdateWire(
      displayName = self?.name?.takeIf { it.isNotBlank() }
        ?: user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email.orEmpty(),
      photoUrl = user.photoURL,
      technicianMirror = self?.toMirrorWire(),
    )

    // A just-redeemed aircraft isn't in the local stores yet — its ref is still syncing down — so it
    // is named explicitly. The ACL check below is what keeps that safe: naming an aircraft we are
    // not actually a member of publishes nothing.
    val targets = (memberships(uid) + listOfNotNull(alsoPublishTo)).distinct()

    targets.forEach { acId ->
      // The ACL decides whether we're a member at all, and with what role. An own aircraft that was
      // never shared has no ACL doc — skip it rather than bootstrapping a share nobody asked for.
      val myRole = shareDoc(acId).get()
        .takeIf { it.exists }
        ?.data<RootWire>()
        ?.memberRoles
        ?.get(uid)
        ?: return@forEach

      val memberDoc = shareDoc(acId).collection(MEMBERS)
        .document(uid)
      if (memberDoc.get().exists) {
        memberDoc.set(update, merge = true)
      } else {
        // No doc yet: the hosting owner, who never redeems. Create it with the role the ACL already
        // grants — rules pin it to exactly that, so this can't mint membership or escalate.
        memberDoc.set(
          MemberCreateWire(
            role = myRole,
            displayName = update.displayName,
            photoUrl = update.photoUrl,
            technicianMirror = update.technicianMirror,
            addedAt = Timestamp.now(),
            invitedBy = uid,
          ),
        )
      }
    }
  }.onFailure { logger.w(it) { "Mirror publish failed; retries on next app start" } }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLinkedTechnicians(): Flow<List<Technician>> {
    val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
    val scope = EntityScope.userRoot(uid)
    return combine(
      refStore.observeAll(scope),
      aircraftStore.observeAll(scope),
    ) { refs, own -> (refs.map { it.id } + own.map { it.id }).distinct() }
      .distinctUntilChanged()
      .flatMapLatest { acIds ->
        if (acIds.isEmpty()) return@flatMapLatest flowOf(emptyList())
        combine(acIds.map { acId -> linkedTechniciansIn(acId, uid) }) { perShare ->
          perShare.toList()
            .flatten()
        }
      }
      // The same person can be in several of your shares — list them once.
      .map { linked -> linked.dedupedByOwner() }
  }

  override fun observeLinkedTechnicians(acId: String): Flow<List<Technician>> {
    val uid = auth.currentUser?.uid ?: return flowOf(emptyList())
    return linkedTechniciansIn(acId, uid).map { it.dedupedByOwner() }
  }

  /**
   * The members of one share who have published a mirror, excluding [selfUid] — the caller's own
   * record is listed separately and comes from their local store, not from a mirror.
   *
   * An aircraft that was never shared just yields an empty roster; a denied read degrades to empty
   * rather than taking the caller's whole list down.
   */
  private fun linkedTechniciansIn(acId: String, selfUid: String): Flow<List<Technician>> =
    shareDoc(acId).collection(MEMBERS).snapshots
      .map { snaps ->
        snaps.documents.mapNotNull { doc ->
          if (doc.id == selfUid) return@mapNotNull null
          doc.data<MemberWire>().technicianMirror?.toTechnician(doc.id)
        }
      }
      .catch { emit(emptyList()) }

  /**
   * Every share the user is a member of: aircraft shared *with* them (the local refs store, per
   * §7.2) plus their own aircraft, which are the shares they host. Own aircraft that were never
   * shared simply have no member doc, and are skipped by the existence check at the call site.
   */
  private suspend fun memberships(uid: String): List<String> {
    val scope = EntityScope.userRoot(uid)
    val shared = refStore.observeAll(scope)
      .first()
      .map { it.id }
    val own = aircraftStore.observeAll(scope)
      .first()
      .map { it.id }
    return (shared + own).distinct()
  }


  /** The device-local share URL for [tokenHash], if this device minted the invite; else null. */
  private suspend fun localInviteUrl(tokenHash: String): String? {
    val uid = auth.currentUser?.uid ?: return null
    return db.schemaQueries.selectConfig(uid, inviteUrlKey(tokenHash))
      .awaitAsOneOrNull()
  }

  private fun inviteUrlKey(tokenHash: String) =
    "$INVITE_URL_KEY_PREFIX$tokenHash"

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
    private val logger = Logger.withTag("SharingManager")
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

/**
 * Flattens the self-technician proto into the plain-field mirror. Certificate fields are optional —
 * an owner doing FAR 43 preventive maintenance with no A&P certificate still publishes a valid
 * name-only mirror (design §7).
 */
private fun Technician.toMirrorWire(): TechnicianMirrorWire =
  TechnicianMirrorWire(
    name = name,
    certificateType = certificate_type.name,
    certNumber = cert_number,
    certExpiration = cert_expiration?.let { Timestamp(it.getEpochSecond(), 0) },
    certExpireLimit = cert_expire_limit.name,
  )

/**
 * Rehydrates a published mirror into a [Technician] snapshot owned by [memberUid]. The uid is both
 * the identity (there is no local record for someone else's profile) and the `source_uid`, which is
 * what marks the entry as first-party rather than hand-typed (§7.3).
 *
 * Enum names are decoded leniently: an unknown value from a newer client degrades to NONE/UNKNOWN
 * rather than throwing and taking out the whole roster.
 */
private fun TechnicianMirrorWire.toTechnician(memberUid: String): Technician =
  Technician(
    id = memberUid,
    name = name,
    certificate_type = certificateType.toCertificateTypeOrNone(),
    cert_number = certNumber,
    cert_expiration = certExpiration?.let {
      toWireInstant(
        it.seconds,
        it.nanoseconds
      )
    },
    cert_expire_limit = certExpireLimit.toCertExpireLimitOrUnknown(),
    source_uid = memberUid,
  )

private fun String.toCertificateTypeOrNone(): CertificateType =
  runCatching { CertificateType.valueOf(this) }
    .getOrDefault(CertificateType.CERTIFICATE_TYPE_NONE)

private fun String.toCertExpireLimitOrUnknown(): CertExpireLimit =
  runCatching { CertExpireLimit.valueOf(this) }
    .getOrDefault(CertExpireLimit.CERT_EXPIRE_LIMIT_UNKNOWN)

/** One entry per owning account, name-ordered. */
private fun List<Technician>.dedupedByOwner(): List<Technician> =
  distinctBy { it.source_uid }
    .sortedBy { it.name.lowercase() }

private fun ShareRole.wire(): String =
  if (this == ShareRole.OWNER) "owner" else "technician"

private fun String.toModel(): ShareRole =
  if (this == "owner") ShareRole.OWNER else ShareRole.TECHNICIAN

private fun ProtoShareRole.toModel(): ShareRole =
  if (this == ProtoShareRole.SHARE_ROLE_OWNER) ShareRole.OWNER else ShareRole.TECHNICIAN

private fun Timestamp.toMillisLong(): Long = toMilliseconds().toLong()

@Serializable
private data class RootWire(
  val hostUid: String = "",
  /** uid → role. Authoritative membership (includes the host); the members subcollection is detail. */
  val memberRoles: Map<String, String> = emptyMap(),
)

/** Self-created member doc — role must match the ACL, which is what rules check. */
@Serializable
private data class MemberCreateWire(
  val role: String,
  val displayName: String,
  val photoUrl: String?,
  val technicianMirror: TechnicianMirrorWire?,
  val addedAt: Timestamp,
  val invitedBy: String,
)

/** Owner-bootstrapped ACL doc: hostUid + the owner's own memberRoles entry (§3.1). */
@Serializable
private data class ShareRootCreateWire(
  val hostUid: String,
  val memberRoles: Map<String, String>,
)

@Serializable
private data class MemberWire(
  val role: String = "technician",
  val displayName: String = "",
  val photoUrl: String? = null,
  val technicianMirror: TechnicianMirrorWire? = null,
)

/**
 * The member's self-technician record, flattened to plain fields so other members can read it
 * without decoding protos out of a private tree (design §7.1, shape §2.1). Written only by the
 * member's own client; certificate fields may be empty (a name-only mirror is still valid).
 */
@Serializable
private data class TechnicianMirrorWire(
  val name: String = "",
  val certificateType: String = "",
  val certNumber: String = "",
  val certExpiration: Timestamp? = null,
  val certExpireLimit: String = "",
)

/** Partial member-doc update: display fields + mirror. `role` is omitted so rules see it unchanged. */
@Serializable
private data class MemberSelfUpdateWire(
  val displayName: String,
  val photoUrl: String?,
  val technicianMirror: TechnicianMirrorWire?,
)

@Serializable
private data class InviteWire(
  val role: String = "technician",
  val createdBy: String = "",
  val createdAt: Timestamp = Timestamp.now(),
  val expiresAt: Timestamp = Timestamp.now(),
  val maxUses: Int = 1,
  val useCount: Int = 0,
  val revoked: Boolean = false,
)

@Serializable
private data class RedeemRequest(val aircraftId: String, val secret: String)
@Serializable
private data class RedeemResponse(
  val aircraftId: String = "",
  val hostUid: String = "",
  val role: String = "technician",
  val alreadyMember: Boolean = false,
)

@Serializable
private data class RevokeRequest(val aircraftId: String, val memberUid: String)
@Serializable
private data class UpdateRoleRequest(
  val aircraftId: String,
  val memberUid: String,
  val role: String,
)
