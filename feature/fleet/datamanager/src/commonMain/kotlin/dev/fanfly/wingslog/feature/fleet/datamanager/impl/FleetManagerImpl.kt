package dev.fanfly.wingslog.feature.fleet.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.ThingInflater
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
  private val templateRegistry: TemplateRegistry,
  storeFactory: EntityStoreFactory,
) : FleetManager {

  private val logger = Logger.withTag("FleetManagerImpl")
  private val store: EntityStore<Thing> =
    storeFactory.create(CollectionKind.Thing)
  private val refStore: EntityStore<SharedAircraftRef> =
    storeFactory.create(CollectionKind.SharedAircraftRef)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeFleetDashboard(): Flow<List<FleetEntry>> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping fleet dashboard observation" }
        flowOf(emptyList())
      } else {
        combine(
          ownThing(user.uid),
          sharedThing(user.uid)
        ) { own, shared ->
          own + shared
        }.catch { e ->
          logger.w(e) { "Fleet observe failed" }
          emit(emptyList())
        }
      }
    }

  /** The user's own thing under their root — always owner. */
  private fun ownThing(uid: String): Flow<List<FleetEntry>> =
    store.observeAll(EntityScope.userRoot(uid))
      .map { rows ->
        rows.map {
          FleetEntry(
            it.value,
            shared = false,
            role = ShareRole.SHARE_ROLE_OWNER
          )
        }
      }

  /**
   * Aircraft shared into the user's fleet: each `SharedAircraftRef` points at a thing doc under
   * its host's root. The refs are pointers, not copies — read the live doc in place (§6.3). A ref
   * whose thing doc hasn't synced yet is skipped rather than shown as a blank card.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun sharedThing(uid: String): Flow<List<FleetEntry>> =
    refStore.observeAll(EntityScope.userRoot(uid))
      .flatMapLatest { refRows ->
        val refs = refRows.map { it.value }
        if (refs.isEmpty()) {
          flowOf(emptyList())
        } else {
          combine(
            refs.map { ref ->
              store.observe(ref.aircraft_id, EntityScope.userRoot(ref.host_uid))
                .map { entity ->
                  entity?.value?.let {
                    FleetEntry(
                      it,
                      shared = true,
                      role = ref.role
                    )
                  }
                }
            }
          ) { entries ->
            entries.filterNotNull()
              .toList()
          }
        }
      }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun loadThing(id: String): Flow<Thing?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping thing observation for $id" }
        flowOf(null)
      } else {
        // The thing doc lives at the *parent* of its nested data: own → users/{myUid}/thing,
        // shared → users/{hostUid}/thing. A ref for this id (keyed by thing id) names the host;
        // its absence means it's the user's own. (The ThingScopeResolver handles the nested
        // thingChildUnsafe scope; the doc itself needs userRoot, hence the lookup here.)
        refStore.observe(id, EntityScope.userRoot(user.uid))
          .flatMapLatest { ref ->
            val rootUid = ref?.value?.host_uid ?: user.uid
            store.observe(id, EntityScope.userRoot(rootUid))
              .map { it?.value }
          }
          .catch { e ->
            logger.w(e) { "Error observing thing $id" }
            emit(null)
          }
      }
    }

  /**
   * The root the thing doc actually lives under: the host's for a shared thing, ours
   * otherwise. A ref keyed by this thing id is what names the host — the same lookup
   * [loadThing] does, and writes have to agree with reads about where the row is.
   *
   * Writing to our own root unconditionally (as this used to) doesn't fail — it silently forks a
   * *second* copy of the thing into our tree, which then reads back as a thing we own.
   */
  private suspend fun rootScopeOf(id: String, uid: String): EntityScope {
    val hostUid = refStore.observe(id, EntityScope.userRoot(uid))
      .first()
      ?.value
      ?.host_uid
    return EntityScope.userRoot(hostUid ?: uid)
  }

  override suspend fun updateThing(thing: Thing): Result<Thing> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot update thing when no user is signed in")
      // A brand-new thing has no id yet, so there is no ref to consult — it is ours by definition.
      val isNew = thing.id.isEmpty()
      val withId =
        if (isNew) thing.copy(id = generateRandomId()) else thing
      // Inflate on every write, not just creation (#717). After the id is assigned: component
      // ids derive from it.
      val inflated =
        ThingInflater.inflate(
          withId,
          templateRegistry.forThingWithFallback(withId)
        )
      val scope =
        if (isNew) EntityScope.userRoot(uid) else rootScopeOf(inflated.id, uid)
      store.put(inflated.id, inflated, scope)
      logger.d { "Thing ${inflated.id} written to local store at ${scope.toPath()}" }
      inflated
    }.onFailure { logger.w(it) { "Error updating thing" } }

  /**
   * Deleting tears the whole share down for every member (§3.3), so it belongs to the hosting owner
   * alone — a co-owner holds the same role but not the thing. The rules reject their tombstone
   * anyway; refusing here means we never queue a write that can only come back denied, which (since
   * #144) a member's client would read as *their own* revocation and purge the share over.
   */
  override suspend fun deleteThing(id: String): Result<Boolean> =
    runCatching {
      val uid = firebaseAuth.currentUser?.uid
        ?: error("Cannot delete thing when no user is signed in")
      val ownRoot = EntityScope.userRoot(uid)
      require(rootScopeOf(id, uid) == ownRoot) {
        "Only the hosting owner may delete thing $id"
      }
      store.delete(id, ownRoot)
      logger.d { "Thing $id tombstoned in local store" }
      true
    }.onFailure { logger.w(it) { "Error deleting aircraft $id" } }
}
