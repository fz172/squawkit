package dev.fanfly.wingslog.feature.fleet.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
  private val store: EntityStore<Aircraft> =
    storeFactory.create(CollectionKind.Aircraft)
  private val refStore: EntityStore<SharedAircraftRef> =
    storeFactory.create(CollectionKind.SharedAircraftRef)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeFleetDashboard(): Flow<List<FleetEntry>> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping fleet dashboard observation" }
        flowOf(emptyList())
      } else {
        combine(ownAircraft(user.uid), sharedAircraft(user.uid)) { own, shared ->
          own + shared
        }.catch { e ->
          logger.w(e) { "Fleet observe failed" }
          emit(emptyList())
        }
      }
    }

  /** The user's own aircraft under their root — always owner. */
  private fun ownAircraft(uid: String): Flow<List<FleetEntry>> =
    store.observeAll(EntityScope.userRoot(uid))
      .map { rows ->
        rows.map { FleetEntry(it.value, shared = false, role = ShareRole.SHARE_ROLE_OWNER) }
      }

  /**
   * Aircraft shared into the user's fleet: each `SharedAircraftRef` points at an aircraft doc under
   * its host's root. The refs are pointers, not copies — read the live doc in place (§6.3). A ref
   * whose aircraft doc hasn't synced yet is skipped rather than shown as a blank card.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun sharedAircraft(uid: String): Flow<List<FleetEntry>> =
    refStore.observeAll(EntityScope.userRoot(uid)).flatMapLatest { refRows ->
      val refs = refRows.map { it.value }
      if (refs.isEmpty()) {
        flowOf(emptyList())
      } else {
        combine(
          refs.map { ref ->
            store.observe(ref.aircraft_id, EntityScope.userRoot(ref.host_uid))
              .map { entity ->
                entity?.value?.let { FleetEntry(it, shared = true, role = ref.role) }
              }
          }
        ) { entries -> entries.filterNotNull().toList() }
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

  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot update aircraft when no user is signed in")
      val withId =
        if (aircraft.id.isEmpty()) aircraft.copy(id = generateRandomId()) else aircraft
      store.put(withId.id, withId, EntityScope.userRoot(uid))
      logger.d { "Aircraft ${withId.id} written to local store" }
      true
    }.onFailure { logger.w(it) { "Error updating aircraft" } }

  override suspend fun deleteAircraft(id: String): Result<Boolean> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot delete aircraft when no user is signed in")
      store.delete(id, EntityScope.userRoot(uid))
      logger.d { "Aircraft $id tombstoned in local store" }
      true
    }.onFailure { logger.w(it) { "Error deleting aircraft $id" } }
}
