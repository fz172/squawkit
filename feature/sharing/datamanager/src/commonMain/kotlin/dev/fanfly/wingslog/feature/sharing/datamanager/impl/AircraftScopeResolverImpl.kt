package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Refs-backed [AircraftScopeResolver]. The member's `shared_aircraft_ref/{aircraftId}` (if any) is
 * keyed by aircraft id and names the host; its absence means the aircraft is the member's own.
 */
class AircraftScopeResolverImpl(
  private val auth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : AircraftScopeResolver {

  private val refStore: EntityStore<SharedAircraftRef> =
    storeFactory.create(CollectionKind.SharedAircraftRef)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun resolve(aircraftId: String): Flow<EntityScope?> =
    auth.authStateChanged.flatMapLatest { user ->
      val uid = user?.uid
      if (uid == null) {
        flowOf(null)
      } else {
        refStore.observe(aircraftId, EntityScope.userRoot(uid))
          .map { ref -> scopeFor(uid, ref?.value?.host_uid, aircraftId) }
          .distinctUntilChanged()
      }
    }

  override suspend fun resolveNow(aircraftId: String): EntityScope {
    val uid = auth.currentUser?.uid
      ?: error("Cannot resolve aircraft scope when no user is signed in")
    val hostUid = refStore.observe(aircraftId, EntityScope.userRoot(uid)).first()?.value?.host_uid
    return scopeFor(uid, hostUid, aircraftId)
  }

  /** Shared when a ref names a foreign host; own otherwise. */
  private fun scopeFor(uid: String, hostUid: String?, aircraftId: String): EntityScope =
    EntityScope.aircraftChild(hostUid ?: uid, aircraftId)
}
