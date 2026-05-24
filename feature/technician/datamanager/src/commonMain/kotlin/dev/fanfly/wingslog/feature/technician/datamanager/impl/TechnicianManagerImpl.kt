package dev.fanfly.wingslog.feature.technician.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.userinfo.UserInfo
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class TechnicianManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
  private val syncPreferences: SyncPreferences,
  storeFactory: EntityStoreFactory,
) : TechnicianManager {

  private val logger = Logger.withTag("TechnicianManagerImpl")
  private val technicianStore: EntityStore<Technician> =
    storeFactory.create(CollectionKind.Technician)
  private val userInfoStore: EntityStore<UserInfo> =
    storeFactory.create(CollectionKind.UserInfo)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  init {
    scope.launch { bootstrapSelfTechnician() }
  }

  private suspend fun bootstrapSelfTechnician() {
    firebaseAuth.authStateChanged.collect { user ->
      if (user == null || user.isAnonymous) return@collect
      ensureSelfProfile()
    }
  }

  override suspend fun ensureSelfProfile(replaceExistingName: Boolean): Result<Unit> = runCatching {
    val user = firebaseAuth.currentUser ?: return@runCatching
    if (user.isAnonymous) return@runCatching
    val uid = user.uid
    val userScope = EntityScope.userRoot(uid)
    val accountName = user.displayName.orEmpty().ifBlank { user.email.orEmpty() }

    // Prefer a locally-known self id; fall back to Firestore (cloud may have one we haven't
    // hydrated yet) so we don't create a duplicate.
    val localSelfId = userInfoStore.observe("main", userScope)
      .firstOrNull()
      ?.value?.self_technician_id?.takeIf { it.isNotBlank() }
    val selfId = localSelfId ?: if (syncPreferences.state.value.cloudSyncEnabled) {
      readSelfIdFromFirestore(uid)
    } else {
      null
    }

    if (selfId.isNullOrBlank()) {
      val newId = generateRandomId()
      technicianStore.put(newId, Technician(id = newId, name = accountName), userScope)
      userInfoStore.put("main", UserInfo(self_technician_id = newId), userScope)
      logger.d { "Self-technician created id=$newId name='$accountName'" }
      return@runCatching
    }

    // Record the id locally if only Firestore knew it.
    if (localSelfId == null) {
      userInfoStore.put("main", UserInfo(self_technician_id = selfId), userScope)
    }
    // Backfill from the account. During guest upgrade, replace the guest-entered self name with
    // the permanent provider name while preserving certificate fields on the existing record.
    val existing = technicianStore.observe(selfId, userScope).firstOrNull()?.value
    if (
      existing != null &&
      accountName.isNotBlank() &&
      (existing.name.isBlank() || replaceExistingName) &&
      existing.name != accountName
    ) {
      technicianStore.put(selfId, existing.copy(name = accountName), userScope)
      logger.d { "Self-technician name backfilled id=$selfId name='$accountName'" }
    }
  }.onFailure { logger.w(it) { "ensureSelfProfile failed" } }

  @OptIn(ExperimentalEncodingApi::class)
  private suspend fun readSelfIdFromFirestore(uid: String): String? = try {
    val snap = firestore
      .collection("users")
      .document(uid)
      .collection("user_info")
      .document("main")
      .get()
    if (snap.exists) {
      val payloadB64 = snap.get<String?>("payload")
      if (payloadB64 != null) {
        val bytes = Base64.decode(payloadB64)
        UserInfo.ADAPTER.decode(bytes).self_technician_id.takeIf { it.isNotBlank() }
      } else null
    } else null
  } catch (e: Exception) {
    logger.w(e) { "Failed to read UserInfo from Firestore for uid=$uid" }
    null
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
            else technicianStore.observe(id, userScope).map { it?.value }
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
}
