package dev.fanfly.wingslog.feature.fleet.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Local-first [FleetManager] backed by [EntityStore]. The sync engine (M3) reads the underlying
 * `dirty=1` rows out of band and pushes them to Firestore — this class never touches Firestore.
 *
 * Auth state still gates observation: when the user signs out we emit `emptyList()` / `null` so
 * stale data does not leak between accounts.
 */
class FleetManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : FleetManager {

  private val logger = Logger.withTag("FleetManagerImpl")
  private val store: EntityStore<Aircraft> = storeFactory.create(CollectionKind.Aircraft)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeFleetDashboard(): Flow<List<Aircraft>> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping fleet dashboard observation" }
        flowOf(emptyList())
      } else {
        store.observeAll(EntityScope.userRoot(user.uid))
          .map { rows -> rows.map { it.value } }
          .catch { e ->
            logger.w(e) { "Fleet observe failed" }
            emit(emptyList())
          }
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun loadAircraft(id: String): Flow<Aircraft?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping aircraft observation for $id" }
        flowOf(null)
      } else {
        store.observe(id, EntityScope.userRoot(user.uid))
          .map { it?.value }
          .catch { e ->
            logger.w(e) { "Error observing aircraft $id" }
            emit(null)
          }
      }
    }

  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> = runCatching {
    val uid = firebaseAuth.currentUser?.uid
      ?: error("Cannot update aircraft when no user is signed in")
    val withId = if (aircraft.id.isEmpty()) aircraft.copy(id = generateRandomId()) else aircraft
    store.put(withId.id, withId, EntityScope.userRoot(uid))
    logger.d { "Aircraft ${withId.id} written to local store" }
    true
  }.onFailure { logger.w(it) { "Error updating aircraft" } }

  override suspend fun deleteAircraft(id: String): Result<Boolean> = runCatching {
    val uid = firebaseAuth.currentUser?.uid
      ?: error("Cannot delete aircraft when no user is signed in")
    store.delete(id, EntityScope.userRoot(uid))
    logger.d { "Aircraft $id tombstoned in local store" }
    true
  }.onFailure { logger.w(it) { "Error deleting aircraft $id" } }
}
