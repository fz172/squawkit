package dev.fanfly.wingslog.feature.squawk.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SquawkManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : SquawkManager {

  private val store: EntityStore<Squawk> = storeFactory.create(CollectionKind.Squawk)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeSquawks(aircraftId: String): Flow<List<Squawk>> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        flowOf(emptyList())
      } else {
        store.observeAll(EntityScope.aircraftChild(user.uid, aircraftId))
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Error observing squawks for aircraft $aircraftId" }
            emit(emptyList())
          }
      }
    }

  override suspend fun addSquawk(aircraftId: String, squawk: Squawk): Result<Boolean> =
    runCatching {
      val uid = requireUid()
      val withId = if (squawk.id.isEmpty()) squawk.copy(id = generateRandomId()) else squawk
      store.put(withId.id, withId, EntityScope.aircraftChild(uid, aircraftId))
      true
    }.onFailure { logger.w(it) { "Error adding squawk" } }

  override suspend fun updateSquawk(aircraftId: String, squawk: Squawk): Result<Boolean> =
    runCatching {
      val uid = requireUid()
      store.put(squawk.id, squawk, EntityScope.aircraftChild(uid, aircraftId))
      true
    }.onFailure { logger.w(it) { "Error updating squawk ${squawk.id}" } }

  override suspend fun deleteSquawk(aircraftId: String, squawkId: String): Result<Boolean> =
    runCatching {
      val uid = requireUid()
      store.delete(squawkId, EntityScope.aircraftChild(uid, aircraftId))
      true
    }.onFailure { logger.w(it) { "Error deleting squawk $squawkId" } }

  override suspend fun markAddressed(
    aircraftId: String,
    squawkIds: List<String>,
    logId: String,
  ): Result<Unit> = runCatching {
    val uid = requireUid()
    val scope = EntityScope.aircraftChild(uid, aircraftId)
    val allSquawks = store.observeAll(scope).firstOrNull()?.associateBy { it.id } ?: emptyMap()
    squawkIds.forEach { squawkId ->
      val existing = allSquawks[squawkId]?.value ?: return@forEach
      store.put(squawkId, existing.copy(addressed_by_log_id = logId), scope)
    }
  }.onFailure { logger.w(it) { "Error marking squawks addressed: $squawkIds" } }

  override suspend fun dismissSquawk(
    aircraftId: String,
    squawkId: String,
    reason: SquawkDismissReason,
  ): Result<Unit> = runCatching {
    val uid = requireUid()
    val scope = EntityScope.aircraftChild(uid, aircraftId)
    val existing = store.observeAll(scope).firstOrNull()
      ?.associateBy { it.id }?.get(squawkId)?.value
      ?: error("Squawk $squawkId not found")
    store.put(
      squawkId,
      existing.copy(
        dismiss_reason = reason,
        dismissed_at = Clock.System.now().toWireInstant(),
      ),
      scope,
    )
  }.onFailure { logger.w(it) { "Error dismissing squawk $squawkId" } }

  override suspend fun reopenSquawk(aircraftId: String, squawkId: String): Result<Unit> =
    runCatching {
      val uid = requireUid()
      val scope = EntityScope.aircraftChild(uid, aircraftId)
      val existing = store.observeAll(scope).firstOrNull()
        ?.associateBy { it.id }?.get(squawkId)?.value
        ?: error("Squawk $squawkId not found")
      store.put(
        squawkId,
        existing.copy(
          dismiss_reason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
          dismissed_at = null,
        ),
        scope,
      )
    }.onFailure { logger.w(it) { "Error reopening squawk $squawkId" } }

  private fun requireUid(): String =
    firebaseAuth.currentUser?.uid ?: error("Cannot mutate squawks when no user is signed in")

  companion object {
    private val logger = Logger.withTag("SquawkManager")
  }
}
