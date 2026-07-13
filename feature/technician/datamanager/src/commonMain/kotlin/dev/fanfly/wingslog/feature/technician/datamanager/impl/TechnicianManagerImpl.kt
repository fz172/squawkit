package dev.fanfly.wingslog.feature.technician.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.userinfo.UserInfo
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateResolution
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class TechnicianManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val cloudSyncSetting: CloudSyncSetting,
  storeFactory: EntityStoreFactory,
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock,
) : TechnicianManager {

  private val logger = Logger.withTag("TechnicianManagerImpl")

  private val technicianStore: EntityStore<Technician> =
    storeFactory.create(CollectionKind.Technician)
  private val userInfoStore: EntityStore<UserInfo> =
    storeFactory.create(CollectionKind.UserInfo)

  /** Bumped after the reviewed flag is written, so the observing flow re-reads it. */
  private val reviewedTrigger = MutableStateFlow(0)

  // This scope coordinates auth events; EntityStore provides its own platform storage context.
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    scope.launch { bootstrapSelfTechnician() }
  }

  private suspend fun bootstrapSelfTechnician() {
    firebaseAuth.authStateChanged.collect { user ->
      if (user == null || user.isAnonymous) return@collect
      ensureSelfProfile()
    }
  }

  override suspend fun ensureSelfProfile(): Result<Unit> =
    runCatching {
      val user = firebaseAuth.currentUser ?: return@runCatching
      if (user.isAnonymous) return@runCatching
      val uid = user.uid
      val userScope = EntityScope.userRoot(uid)
      val accountName = user.displayName.orEmpty()
        .ifBlank { user.email.orEmpty() }

      // Prefer a locally-known self id; otherwise wait briefly for sync hydration to land the
      // cloud's UserInfo locally so we don't create a duplicate.
      val localSelfId = userInfoStore.observe("main", userScope)
        .firstOrNull()
        ?.value?.self_technician_id?.takeIf { it.isNotBlank() }
      val selfId = localSelfId ?: awaitHydratedSelfId(userScope)

      if (selfId.isNullOrBlank()) {
        val newId = generateRandomId()
        technicianStore.put(
          newId,
          Technician(id = newId, name = accountName),
          userScope
        )
        userInfoStore.put(
          "main",
          UserInfo(self_technician_id = newId),
          userScope
        )
        logger.d { "Self-technician created id=$newId name='$accountName'" }
        return@runCatching
      }

      // Record the id locally if only Firestore knew it.
      if (localSelfId == null) {
        userInfoStore.put(
          "main",
          UserInfo(self_technician_id = selfId),
          userScope
        )
      }
      // Seed a BLANK name from the account — never overwrite one the user has set. The account name
      // is a starting point for an empty profile, not an authority over a filled-in one.
      val existing = technicianStore.observe(selfId, userScope)
        .firstOrNull()?.value
      if (
        existing != null &&
        accountName.isNotBlank() &&
        existing.name.isBlank()
      ) {
        technicianStore.put(
          selfId,
          existing.copy(name = accountName),
          userScope
        )
        logger.d { "Self-technician name backfilled id=$selfId name='$accountName'" }
      }
    }.onFailure { logger.w(it) { "ensureSelfProfile failed" } }

  /**
   * The cloud may know a self id we haven't hydrated yet — sync pulls `UserInfo` on sign-in.
   * Rather than reading Firestore here (the sync engine is the only Firestore client), wait
   * for hydration to land the row in the local store. A fresh account has nothing to hydrate,
   * so the wait is bounded: on timeout the caller creates a new profile, matching the old
   * Firestore-read behavior when the read came back empty or offline.
   */
  private suspend fun awaitHydratedSelfId(userScope: EntityScope): String? {
    if (!cloudSyncSetting.isCloudSyncEnabled()) return null
    return withTimeoutOrNull(SELF_ID_HYDRATION_TIMEOUT) {
      userInfoStore.observe("main", userScope)
        .mapNotNull { it?.value?.self_technician_id?.takeIf { id -> id.isNotBlank() } }
        .first()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeSelfId(): Flow<String?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) flowOf(null)
      else userInfoStore.observe("main", EntityScope.userRoot(user.uid))
        .map { it?.value?.self_technician_id?.takeIf { id -> id.isNotBlank() } }
        .catch { e ->
          logger.w(e) { "Error observing UserInfo" }
          emit(null)
        }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeSelf(): Flow<Technician?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) flowOf(null)
      else {
        val userScope = EntityScope.userRoot(user.uid)
        userInfoStore.observe("main", userScope)
          .map { it?.value?.self_technician_id?.takeIf { id -> id.isNotBlank() } }
          .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(null)
            else technicianStore.observe(id, userScope)
              .map { it?.value }
              .catch { e ->
                logger.w(e) { "Error observing self technician $id" }
                emit(null)
              }
          }
          .catch { e ->
            logger.w(e) { "Error observing UserInfo" }
            emit(null)
          }
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeTechnicians(): Flow<List<Technician>> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping technicians observation" }
        flowOf(emptyList())
      } else {
        technicianStore.observeAll(EntityScope.userRoot(user.uid))
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Technician observe failed" }
            emit(emptyList())
          }
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun loadTechnician(id: String): Flow<Technician?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping technician observation for $id" }
        flowOf(null)
      } else {
        technicianStore.observe(id, EntityScope.userRoot(user.uid))
          .map { it?.value }
          .catch { e ->
            logger.w(e) { "Error observing technician $id" }
            emit(null)
          }
      }
    }

  override suspend fun updateTechnician(technician: Technician): Result<Boolean> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot update technician when no user is signed in")
      val withId =
        if (technician.id.isEmpty()) technician.copy(id = generateRandomId()) else technician
      technicianStore.put(withId.id, withId, EntityScope.userRoot(uid))
      logger.d { "Technician ${withId.id} written to local store" }
      true
    }.onFailure { logger.w(it) { "Error updating technician" } }

  override suspend fun deleteTechnician(id: String): Result<Boolean> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot delete technician when no user is signed in")
      technicianStore.delete(id, EntityScope.userRoot(uid))
      logger.d { "Technician $id tombstoned in local store" }
      true
    }.onFailure { logger.w(it) { "Error deleting technician $id" } }

  override suspend fun applyDuplicateMerges(
    groups: List<DuplicateGroup>,
    reviewedSignature: String,
  ): Result<Unit> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot merge technicians when no user is signed in")
      val userScope = EntityScope.userRoot(uid)

      groups.forEach { group ->
        when (group.resolution) {
          // Both merges delete the duplicate rows. They are hand-typed and now redundant — the
          // keeper is either a richer manual row or the member's live mirror. Logs hold their own
          // snapshots, so already-signed work keeps the technician it recorded.
          DuplicateResolution.MERGE_MANUAL,
          DuplicateResolution.MERGE_INTO_MEMBER,
          -> group.duplicates.forEach { technicianStore.delete(it.id, userScope) }

          // Two members are two accounts; this is a heads-up about a likely mistyped certificate,
          // not something to apply.
          DuplicateResolution.WARN_MIRROR_CONFLICT -> Unit
        }
      }
      markDuplicatesReviewed(reviewedSignature).getOrThrow()
    }.onFailure { logger.w(it) { "Error applying technician merges" } }

  /**
   * Must hang off `authStateChanged`, like every other observe here — NOT off a one-shot read of
   * `currentUser`. Firebase restores the session asynchronously, so a ViewModel built before that
   * lands would read a null user and mis-report the review state for its entire lifetime.
   *
   * The trigger re-reads after [markDuplicatesReviewed], so dismissing takes effect immediately
   * rather than at the next screen recreation.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeReviewedDuplicatesSignature(): Flow<String?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) flowOf(null)
      else reviewedTrigger.map {
        db.schemaQueries.selectConfig(user.uid, DUPLICATES_REVIEWED_KEY)
          .awaitAsOneOrNull()
      }
    }.catch { e ->
      // A failed read must not silently hide the prompt — showing one the user can dismiss is
      // recoverable, swallowing the feature with no trace is not.
      logger.w(e) { "Could not read reviewed-duplicates signature" }
      emit(null)
    }

  override suspend fun markDuplicatesReviewed(signature: String): Result<Unit> = runCatching {
    val uid = firebaseAuth.currentUser?.uid ?: return@runCatching
    writeLock.withLock {
      db.schemaQueries.upsertConfig(uid, DUPLICATES_REVIEWED_KEY, signature)
    }
    reviewedTrigger.update { it + 1 }
  }

  override suspend fun saveSelfName(name: String): Result<Unit> = runCatching {
    val uid = firebaseAuth.currentUser?.uid
      ?: error("Cannot save name when no user is signed in")
    val userScope = EntityScope.userRoot(uid)
    val existingSelfId = userInfoStore
      .observe("main", userScope)
      .firstOrNull()
      ?.value?.self_technician_id?.takeIf { it.isNotBlank() }
    if (existingSelfId != null) {
      val existing = technicianStore.observe(existingSelfId, userScope)
        .firstOrNull()?.value
      if (existing != null) {
        technicianStore.put(
          existingSelfId,
          existing.copy(name = name),
          userScope
        )
      }
    } else {
      val newId = generateRandomId()
      technicianStore.put(newId, Technician(id = newId, name = name), userScope)
      userInfoStore.put("main", UserInfo(self_technician_id = newId), userScope)
    }
    logger.d { "Self-technician name saved: $name" }
  }.onFailure { logger.w(it) { "Error saving self name" } }

  companion object {
    private val SELF_ID_HYDRATION_TIMEOUT = 5.seconds

    /**
     * Device-local: the review prompt is a nudge, not data worth syncing. Holds the *signature* of
     * the duplicate set last reviewed — see [observeReviewedDuplicatesSignature]. A legacy "1" from
     * the boolean version simply never matches a real signature, so it self-heals into a re-prompt.
     */
    private const val DUPLICATES_REVIEWED_KEY = "technician_duplicates_reviewed"

  }
}
