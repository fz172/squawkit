package dev.fanfly.wingslog.core.sync

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Top-level orchestrator that wires Firestore sync to the local store for the signed-in user.
 *
 * Lifecycle is anchored to [FirebaseAuth.authStateChanged]:
 * - On sign-in (non-anonymous user): hydrate top-level scopes (Aircraft, Technician, LicenseInfo)
 *   under the user's root, attach pull listeners with the cursor watermark, launch [PushWorker],
 *   and observe the local aircraft list to spin up per-aircraft pull listeners for nested
 *   collections (MaintenanceLog/Task/Overview).
 * - On sign-out: cancel everything by tearing down the per-user scope. The next sign-in starts a
 *   fresh scope; data already on disk is left alone (a different user starts with their own
 *   `users/{uid}/...` scope, so there's no leakage).
 *
 * R1 keeps things deliberately simple: hydrate→listen on every sign-in (idempotent thanks to the
 * `INSERT OR REPLACE` upsert) and skip cursor-driven backoff retries — those land in a follow-up.
 */
class SyncEngine(
  private val auth: FirebaseAuth,
  private val cursors: SyncCursorStore,
  private val writer: SyncWriter,
  private val fetcher: RemoteFetcher,
  private val pullSubscription: FirestorePullSubscription,
  private val hydrationRunner: HydrationRunner,
  private val pullListenerFactory: (CollectionKind, EntityScope) -> PullListener,
  private val pushWorker: PushWorker,
  private val storeFactory: EntityStoreFactory,
  private val ioContext: CoroutineContext,
) {

  private val log = Logger.withTag(TAG)

  private val supervisor = SupervisorJob()
  private val rootScope = CoroutineScope(supervisor + ioContext)

  /** Scope spun up per signed-in user; cancelled when the user changes or signs out. */
  private var userScope: CoroutineScope? = null

  /**
   * Idempotent. Safe to call from app startup. The returned [Job] cancels the engine and tears
   * down all per-user work.
   */
  fun start(): Job {
    return rootScope.launch {
      auth.authStateChanged.collectLatest { user ->
        userScope?.cancel()
        userScope = null
        if (user == null || user.isAnonymous) {
          log.i { "auth state: signed out (or anonymous); sync idle" }
          return@collectLatest
        }
        log.i { "auth state: signed in as ${user.uid}; starting sync" }
        val scope = CoroutineScope(SupervisorJob(supervisor) + ioContext)
        userScope = scope
        runForUser(user, scope)
      }
    }
  }

  fun stop() {
    rootScope.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun runForUser(user: FirebaseUser, scope: CoroutineScope) {
    val uid = user.uid
    val userRoot = EntityScope.userRoot(uid)

    scope.launch { pushWorker.run() }

    for (kind in TOP_LEVEL_KINDS) {
      scope.launch { hydrateAndListen(uid, kind, userRoot) }
    }

    val aircraftStore: EntityStore<Aircraft> = storeFactory.create(CollectionKind.Aircraft)
    scope.launch {
      aircraftStore.observeAll(userRoot)
        .map { rows -> rows.map { it.id }.toSet() }
        .distinctUntilChanged()
        .collectLatest { aircraftIds ->
          aircraftSubScopeSupervisor.cancel()
          val subSupervisor = SupervisorJob(scope.coroutineContext[Job])
          aircraftSubScopeSupervisor = subSupervisor
          val subScope = CoroutineScope(subSupervisor + ioContext)
          for (aircraftId in aircraftIds) {
            val acScope = EntityScope.aircraftChild(uid, aircraftId)
            for (kind in PER_AIRCRAFT_KINDS) {
              subScope.launch { hydrateAndListen(uid, kind, acScope) }
            }
          }
        }
    }
  }

  /** Per-cycle supervisor for the aircraft-child listeners; recreated each time the id-set changes. */
  private var aircraftSubScopeSupervisor: Job = Job().apply { complete() }

  private suspend fun hydrateAndListen(uid: String, kind: CollectionKind, scope: EntityScope) {
    val cursor = cursors.get(uid, kind, scope)
    if (cursor?.hydrated != true) {
      val ok = hydrationRunner.runFor(uid, kind, scope)
      if (!ok) {
        log.w { "hydration failed for ${kind.wireName} ${scope.toPath()}; skipping listener this cycle" }
        return
      }
    }
    val watermark = cursors.get(uid, kind, scope)?.lastSeenRemote
    val listener = pullListenerFactory(kind, scope)
    pullSubscription.observe(kind, scope, watermark).collect { remotes ->
      if (remotes.isEmpty()) return@collect
      var maxTs = Long.MIN_VALUE
      for (remote in remotes) {
        val applied = listener.apply(remote)
        if (applied > maxTs) maxTs = applied
      }
      if (maxTs != Long.MIN_VALUE) {
        cursors.advanceLastSeen(uid, kind, scope, maxTs)
      }
    }
  }

  companion object {
    private const val TAG = "SyncEngine"

    /** Collections that live at `users/{uid}/<wire>/...`. Hydrated on sign-in. */
    private val TOP_LEVEL_KINDS: List<CollectionKind> = listOf(
      CollectionKind.Aircraft,
      CollectionKind.Technician,
      CollectionKind.LicenseInfo,
    )

    /** Collections nested under `users/{uid}/aircraft/{ac}/<wire>/...`. Hydrated per aircraft. */
    private val PER_AIRCRAFT_KINDS: List<CollectionKind> = listOf(
      CollectionKind.MaintenanceLog,
      CollectionKind.MaintenanceTask,
      CollectionKind.MaintenanceOverview,
    )
  }
}
