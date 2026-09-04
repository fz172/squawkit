package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.model.technician.resolvedCertifications
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.InviteLink
import dev.fanfly.wingslog.feature.sharing.model.InvitePreview
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.RedeemOutcome
import dev.fanfly.wingslog.feature.sharing.model.SHARE_URL_BASE
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.model.ThingShareState
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.thing.CertExpireLimit
import dev.fanfly.wingslog.thing.CertificateType
import dev.fanfly.wingslog.thing.Certification
import dev.fanfly.wingslog.thing.Technician
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.toMilliseconds
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import dev.fanfly.wingslog.core.model.sharing.ShareRole as ProtoShareRole

/**
 * Online-only Firestore/Functions client for thing sharing (docs/sharing §6.2). Reads of the
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
  private val functions: FirebaseFunctions,
) : SharingManager {
  private val refStore =
    storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)
  private val thingStore =
    storeFactory.create<Thing>(CollectionKind.Thing)

  /**
   * The ACL for [acId] under [hostUid]. Keyed by host since #204: a thing id is unique only
   * within a tree, so a globally-keyed ACL could be claimed by anyone who knew the id.
   */
  private fun shareDoc(hostUid: String, acId: String) =
    firestore.collection(SHARES)
      .document(hostUid)
      .collection(SHARE_THING)
      .document(acId)

  /**
   * Whose tree this thing lives in — ours if we own it, otherwise the host named on our ref.
   * Answered from the local stores, so it is available offline and needs no round trip.
   *
   * Null when neither is present: we are not the owner and hold no ref, so we have no business
   * building a path into anyone's share.
   */
  private fun observeHostUid(acId: String): Flow<String?> {
    val uid = auth.currentUser?.uid ?: return flowOf(null)
    val scope = EntityScope.userRoot(uid)
    return combine(
      thingStore.observe(acId, scope),
      refStore.observe(acId, scope),
    ) { own, ref ->
      when {
        own != null -> uid
        ref != null -> ref.value.host_uid
        else -> null
      }
    }.distinctUntilChanged()
  }

  /** One-shot [observeHostUid], for the suspend paths (invite create/cancel, callables). */
  private suspend fun hostUidFor(acId: String): String =
    observeHostUid(acId).first()
      ?: error("Not a member of thing $acId; cannot resolve its share")

  /**
   * Bumped after a successful [createInvite] to force the roster listener in [observeShareState]
   * to resubscribe. A denied `.snapshots()` listener (no ACL yet) is CLOSED by the underlying SDK —
   * there is no retry once the rules start allowing the read, so without this the very first invite
   * on a never-shared thing would bootstrap the ACL server-side while our still-dead listener
   * never notices, leaving the roster (and the invite the CODE view is about to show) stuck.
   */
  private val rosterRetryTrigger = MutableStateFlow(0)

  override fun observeShareState(acId: String): Flow<ThingShareState> =
    combine(observeHostUid(acId), rosterRetryTrigger) { hostUid, _ -> hostUid }
      .flatMapLatest { hostUid ->
        if (hostUid == null) flowOf(ThingShareState()) else shareStateIn(
          hostUid,
          acId
        )
      }

  private fun shareStateIn(
    hostUid: String,
    acId: String
  ): Flow<ThingShareState> {
    val myUid = auth.currentUser?.uid
    val root = shareDoc(hostUid, acId).snapshots.map {
      it.takeIf { s -> s.exists }
        ?.data<RootWire>()
    }
    val members = shareDoc(hostUid, acId).collection(MEMBERS).snapshots
    // Invites are owner-only in the rules, so a technician's listener is denied. Degrade to "no
    // pending invites" rather than letting that failure take down the combine — the roster IS
    // readable by any member, and it's what backs their read-only view and the Leave action.
    val invites: Flow<List<PendingInvite>> =
      shareDoc(hostUid, acId).collection(INVITES).snapshots
        .map { inviteSnaps ->
          // No `revoked` / `useCount` to filter on any more: cancelling and redeeming both DELETE the
          // code, so a doc that still exists is a live invite (#164). Expired ones are filtered by
          // time — the TTL sweep may not have reaped them yet.
          val now = Clock.System.now()
            .toEpochMilliseconds()
          inviteSnaps.documents
            .map { it.id to it.data<InviteWire>() }
            .map { (codeId, i) ->
              PendingInvite(
                codeId = codeId,
                role = i.role.toModel(),
                createdAtEpochMs = i.createdAt.toMillisLong(),
                expiresAtEpochMs = i.expiresAt.toMillisLong(),
              )
            }
            .filter { it.expiresAtEpochMs > now }
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
      ThingShareState(
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
        state.copy(invites = state.invites.map {
          it.copy(
            code = localInviteCode(
              it.codeId
            )
          )
        })
      }
      // A revoked member's roster listener is denied the instant they leave `memberRoles` — well
      // before the ref tombstone reaches their device. Surface that as state rather than an error:
      // it is the earliest signal a member has that their access ended, and the screen watching this
      // roster has to react to it. Rules don't flap, so a denial is a real answer, not a hiccup.
      //
      // On our OWN thing, though, a denial means something else: there is no ACL yet — no invite
      // has ever been created (aircraft_shares match block) — not a revocation. Stand in as the sole
      // member from the locally-known owner role instead of surfacing a dead-end "no access"; the
      // real member doc backfills itself (§7.2) once the first invite bootstraps the share.
      .catch { e ->
        if (!isPermissionDenied(e)) throw e
        if (hostUid == myUid) {
          val user = auth.currentUser
          emit(
            ThingShareState(
              members = listOf(
                ShareMember(
                  uid = hostUid,
                  displayName = user?.displayName.orEmpty(),
                  role = ShareRole.OWNER,
                  photoUrl = user?.photoURL,
                  isHost = true,
                  isSelf = true,
                ),
              ),
            ),
          )
        } else {
          emit(ThingShareState(accessDenied = true))
        }
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
    // Own thing ⇒ owner; otherwise the ref's advisory role; otherwise not a member.
    return combine(
      thingStore.observe(acId, scope),
      refStore.observe(acId, scope),
    ) { own, ref ->
      when {
        own != null -> ShareRole.OWNER
        ref != null -> ref.value.role.toModel()
        else -> null
      }
    }
  }

  override fun observeIsForeignHosted(acId: String): Flow<Boolean> {
    val uid = auth.currentUser?.uid ?: return flowOf(false)
    // A ref exists iff the thing was shared *into* this fleet — exactly the foreign-hosted case,
    // and the co-owner-on-a-foreign-tree case too, since a co-owner also holds a ref.
    return refStore.observe(acId, EntityScope.userRoot(uid))
      .map { it != null }
      .distinctUntilChanged()
  }

  override suspend fun createInvite(
    acId: String,
    role: ShareRole,
    thingLabel: String,
  ): Result<InviteLink> = runCatching {
    val res = functions.httpsCallable("createThingShareInvite")
      .invoke(
        CreateInviteRequest(
          thingId = acId,
          role = role.wire(),
          thingLabel = thingLabel,
        ),
      )
      .data<CreateInviteResponse>()

    // Cache the code on THIS device so the owner can re-show it. The server keeps only the hash, so
    // if this cache is lost (reinstall, other device) the code is gone for good — cancel and mint a
    // new one. That is the trade for a code nobody can look up (#164).
    val uid = requireUid()
    writeLock.withLock {
      db.schemaQueries.upsertConfig(uid, inviteCodeKey(res.codeId), res.code)
    }

    // The function just bootstrapped the ACL server-side. If this was the first invite on a
    // never-shared thing, our roster listener was denied before the ACL existed and the SDK
    // closed it for good — resubscribe so the CODE view about to open can actually see this invite.
    rosterRetryTrigger.update { it + 1 }

    InviteLink(
      code = res.code,
      formattedCode = res.formattedCode,
      codeId = res.codeId,
      url = "$SHARE_URL_BASE#${res.code}", // names no thing, no host — only the code
      expiresAtEpochMs = res.expiresAtMs,
    )
  }

  override suspend fun cancelInvite(
    acId: String,
    codeId: String
  ): Result<Unit> = runCatching {
    functions.httpsCallable("cancelThingShareInvite")
      .invoke(CancelInviteRequest(thingId = acId, codeId = codeId))
    val uid = requireUid()
    writeLock.withLock {
      db.schemaQueries.deleteConfig(
        uid,
        inviteCodeKey(codeId)
      )
    }
    Unit
  }

  override suspend fun redeemInvite(code: String): Result<RedeemOutcome> =
    runCatching {
      val res = functions.httpsCallable("redeemThingShareInvite")
        .invoke(RedeemRequest(code = code))
        .data<RedeemResponse>()
      RedeemOutcome(
        thingId = res.thingId,
        hostUid = res.hostUid,
        role = res.role.toModel(),
        alreadyMember = res.alreadyMember,
      )
    }

  override suspend fun previewInvite(code: String): Result<InvitePreview> =
    runCatching {
      val res = functions.httpsCallable("previewThingShareInvite")
        .invoke(PreviewRequest(code = code))
        .data<PreviewResponse>()
      InvitePreview(
        thingLabel = res.thingLabel,
        hostName = res.hostName,
        role = res.role.toModel(),
      )
    }

  override suspend fun revokeMember(acId: String, uid: String): Result<Unit> =
    runCatching {
      functions.httpsCallable("revokeThingShare")
        .invoke(
          RevokeRequest(
            hostUid = hostUidFor(acId),
            thingId = acId,
            memberUid = uid,
          ),
        )
    }

  override suspend fun updateRole(
    acId: String,
    uid: String,
    role: ShareRole
  ): Result<Unit> = runCatching {
    functions.httpsCallable("updateThingShareRole")
      .invoke(
        UpdateRoleRequest(
          hostUid = hostUidFor(acId),
          thingId = acId,
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
  override suspend fun publishTechnicianMirror(alsoPublishTo: String?): Result<Unit> =
    runCatching {
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

      // A just-redeemed thing isn't in the local stores yet — its ref is still syncing down — so it
      // is named explicitly. The ACL check below is what keeps that safe: naming a thing we are
      // not actually a member of publishes nothing.
      val targets = (memberships(uid) + listOfNotNull(alsoPublishTo)).distinct()

      targets.forEach { acId ->
        // Each target sits in its host's namespace (#204): our own thing under us, a shared one
        // under whoever hosts it. A target we can place in neither is not ours to publish into.
        val hostUid = observeHostUid(acId).first() ?: return@forEach

        // The ACL decides whether we're a member at all, and with what role. An own thing that was
        // never shared has no ACL doc — skip it rather than bootstrapping a share nobody asked for.
        val myRole = shareDoc(hostUid, acId).get()
          .takeIf { it.exists }
          ?.data<RootWire>()
          ?.memberRoles
          ?.get(uid)
          ?: return@forEach

        val memberDoc = shareDoc(hostUid, acId).collection(MEMBERS)
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
      thingStore.observeAll(scope),
    ) { refs, own -> (refs.map { it.id } + own.map { it.id }).distinct() }
      .distinctUntilChanged()
      .flatMapLatest { acIds ->
        if (acIds.isEmpty()) return@flatMapLatest flowOf(emptyList())
        combine(acIds.map { acId ->
          linkedTechniciansIn(
            acId,
            uid
          )
        }) { perShare ->
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
   * A thing that was never shared just yields an empty roster; a denied read degrades to empty
   * rather than taking the caller's whole list down.
   */
  private fun linkedTechniciansIn(
    acId: String,
    selfUid: String
  ): Flow<List<Technician>> =
    observeHostUid(acId).flatMapLatest { hostUid ->
      if (hostUid == null) flowOf(emptyList()) else membersOf(
        hostUid,
        acId,
        selfUid
      )
    }

  private fun membersOf(
    hostUid: String,
    acId: String,
    selfUid: String,
  ): Flow<List<Technician>> =
    shareDoc(hostUid, acId).collection(MEMBERS).snapshots
      .map { snaps ->
        snaps.documents.mapNotNull { doc ->
          if (doc.id == selfUid) return@mapNotNull null
          doc.data<MemberWire>().technicianMirror?.toTechnician(doc.id)
        }
      }
      .catch { emit(emptyList()) }

  /**
   * Every share the user is a member of: thing shared *with* them (the local refs store, per
   * §7.2) plus their own thing, which are the shares they host. Own thing that were never
   * shared simply have no member doc, and are skipped by the existence check at the call site.
   */
  private suspend fun memberships(uid: String): List<String> {
    val scope = EntityScope.userRoot(uid)
    val shared = refStore.observeAll(scope)
      .first()
      .map { it.id }
    val own = thingStore.observeAll(scope)
      .first()
      .map { it.id }
    return (shared + own).distinct()
  }

  /**
   * The code for [codeId], if this device minted it. The server stores only the hash, so this cache
   * is the ONLY way to re-show a code — lose it (reinstall, another device) and the invite must be
   * cancelled and re-minted. That is the price of a code nobody can look up (#164).
   */
  private suspend fun localInviteCode(codeId: String): String? {
    val uid = auth.currentUser?.uid ?: return null
    return db.schemaQueries.selectConfig(uid, inviteCodeKey(codeId))
      .awaitAsOneOrNull()
  }

  private fun inviteCodeKey(codeId: String) =
    "$INVITE_URL_KEY_PREFIX$codeId"

  private fun requireUid(): String =
    auth.currentUser?.uid ?: error("Sharing requires a signed-in user")

  companion object {
    private val logger = Logger.withTag("SharingManager")

    // MIGRATION (Phase G3, docs/product/thing_migration_design.md §5.4): the ACL tree moved from
    // aircraft_shares/{hostUid}/thing/{acId} to thing_shares/{hostUid}/thing/{acId}.
    //
    // The constant NAMES keep saying "aircraft" deliberately — only their values move. §3.3 draws
    // the line there: rename what is stored identity, leave alone what is only a name in code.
    //
    // This ships in the SAME release as two backend changes, and is meaningless without them:
    // sharingModels.ts's matching constants, and firestore.rules' shareRole() repoint. A client on
    // this build talking to a backend without them reads an ACL nobody writes; the reverse reads
    // one nobody authorizes against. Either way a member looks like they lost access.
    private const val SHARES = "thing_shares"
    private const val SHARE_THING = "thing"
    private const val MEMBERS = "members"
    private const val INVITES = "invites"

    // Matches the App Link / Universal Link host verified for deep linking (see AndroidManifest and
    // ThingShareDeepLinks) so the link opens the app; the web app serves the same URL as a fallback.
    private const val INVITE_TTL_SECONDS = 7L * 24 * 60 * 60
    private const val INVITE_URL_KEY_PREFIX = "share_invite_url:"
  }
}

/**
 * Flattens the self-technician proto into the plain-field mirror. Certifications are optional — an
 * owner doing FAR 43 preventive maintenance with no A&P certificate still publishes a valid
 * name-only mirror (design §7).
 *
 * Goes through `resolvedCertifications`, so a member whose own record still carries the pre-#684
 * single certificate publishes it as one certification rather than as nothing. The legacy flat
 * fields are deliberately left unwritten: they are a read path, and two answers to the same
 * question that nothing keeps in step is what the repeated field replaced.
 */
internal fun Technician.toMirrorWire(): TechnicianMirrorWire =
  TechnicianMirrorWire(
    name = name,
    certifications = resolvedCertifications().map {
      CertificationWire(
        type = it.type,
        number = it.number,
        expiration = it.expiration?.let { ts ->
          Timestamp(
            ts.getEpochSecond(),
            0
          )
        },
        expireLimit = it.expire_limit.name,
        label = it.label,
      )
    },
  )

/**
 * Rehydrates a published mirror into a [Technician] snapshot owned by [memberUid]. The uid is both
 * the identity (there is no local record for someone else's profile) and the `source_uid`, which is
 * what marks the entry as first-party rather than hand-typed (§7.3).
 *
 * Enum names are decoded leniently: an unknown value from a newer client degrades to NONE/UNKNOWN
 * rather than throwing and taking out the whole roster.
 */
internal fun TechnicianMirrorWire.toTechnician(memberUid: String): Technician =
  Technician(
    id = memberUid,
    name = name,
    certifications = certifications.map {
      Certification(
        type = it.type,
        number = it.number,
        expiration = it.expiration?.let { ts ->
          toWireInstant(
            ts.seconds,
            ts.nanoseconds
          )
        },
        expire_limit = it.expireLimit.toCertExpireLimitOrUnknown(),
        label = it.label,
      )
    },
    // The legacy flat fields go back into the legacy proto fields, so a mirror from an older client
    // resolves through exactly the transitional path a local record of the same vintage does.
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
internal data class TechnicianMirrorWire(
  val name: String = "",
  /**
   * The single FAA certificate a mirror carried before #684. **Read-only**: a current client
   * publishes [certifications] instead, and these survive only so a mirror last written by an
   * older build still resolves.
   */
  val certificateType: String = "",
  val certNumber: String = "",
  val certExpiration: Timestamp? = null,
  val certExpireLimit: String = "",
  /** Every credential the member holds — one person can be both an A&P and an ASE mechanic. */
  val certifications: List<CertificationWire> = emptyList(),
)

/** One certification, flattened the same way and for the same reason as its parent. */
@Serializable
internal data class CertificationWire(
  val type: String = "",
  val number: String = "",
  val expiration: Timestamp? = null,
  val expireLimit: String = "",
  /** The member's own word for a credential no template declares — empty otherwise. */
  val label: String = "",
)

/** Partial member-doc update: display fields + mirror. `role` is omitted so rules see it unchanged. */
@Serializable
private data class MemberSelfUpdateWire(
  val displayName: String,
  val photoUrl: String?,
  val technicianMirror: TechnicianMirrorWire?,
)

/** The owner-visible pending-invite record. Deliberately holds no code — only its hash, as the id. */
@Serializable
private data class InviteWire(
  val role: String = "technician",
  val createdBy: String = "",
  val createdAt: Timestamp = Timestamp.now(),
  val expiresAt: Timestamp = Timestamp.now(),
)

@Serializable
private data class CreateInviteRequest(
  val thingId: String,
  val role: String,
  val thingLabel: String,
)

@Serializable
private data class CreateInviteResponse(
  val code: String = "",
  val formattedCode: String = "",
  val codeId: String = "",
  val expiresAtMs: Long = 0L,
)

@Serializable
private data class CancelInviteRequest(
  val thingId: String,
  val codeId: String
)

@Serializable
private data class PreviewRequest(val code: String)

@Serializable
private data class PreviewResponse(
  val thingLabel: String = "",
  val hostName: String = "",
  val role: String = "technician",
)

@Serializable
private data class RedeemRequest(val code: String)

@Serializable
private data class RedeemResponse(
  val thingId: String = "",
  val hostUid: String = "",
  val role: String = "technician",
  val alreadyMember: Boolean = false,
)

@Serializable
private data class RevokeRequest(
  val hostUid: String,
  val thingId: String,
  val memberUid: String,
)

@Serializable
private data class UpdateRoleRequest(
  val hostUid: String,
  val thingId: String,
  val memberUid: String,
  val role: String,
)
