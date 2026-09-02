package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
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
 * Refs-backed [ThingScopeResolver]. The member's `shared_aircraft_ref/{thingId}` (if any) is
 * keyed by thing id and names the host; its absence means the thing is the member's own.
 */
class ThingScopeResolverImpl(
  private val auth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : ThingScopeResolver {

  private val refStore: EntityStore<SharedAircraftRef> =
    storeFactory.create(CollectionKind.SharedAircraftRef)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun resolve(thingId: String): Flow<EntityScope?> =
    auth.authStateChanged.flatMapLatest { user ->
      val uid = user?.uid
      if (uid == null) {
        flowOf(null)
      } else {
        refStore.observe(thingId, EntityScope.userRoot(uid))
          .map { ref -> scopeFor(uid, ref?.value?.host_uid, thingId) }
          .distinctUntilChanged()
      }
    }

  override suspend fun resolveNow(thingId: String): EntityScope {
    val uid = auth.currentUser?.uid
      ?: error("Cannot resolve thing scope when no user is signed in")
    val hostUid = refStore.observe(thingId, EntityScope.userRoot(uid)).first()?.value?.host_uid
    return scopeFor(uid, hostUid, thingId)
  }

  /** Shared when a ref names a foreign host; own otherwise. */
  private fun scopeFor(uid: String, hostUid: String?, thingId: String): EntityScope =
    EntityScope.thingChildUnsafe(hostUid ?: uid, thingId)
}
