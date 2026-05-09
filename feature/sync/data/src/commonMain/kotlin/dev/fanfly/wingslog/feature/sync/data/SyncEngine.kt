package dev.fanfly.wingslog.feature.sync.data

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.attachment.datamanager.UploadScheduler
import dev.fanfly.wingslog.feature.sync.data.SyncEngine.Companion.PUSH_FAILURE_KEY
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Top-level orchestrator that wires Firestore sync to the local store for the signed-in user.
 *
 * Lifecycle is anchored to [FirebaseAuth.authStateChanged]:
 * - On sign-in (non-anonymous user): hydrate top-level scopes (Aircraft, Technician, UserInfo)
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
  private val pullSubscription: FirestorePullSubscription,
  private val hydrationRunner: HydrationRunner,
  private val pullListenerFactory: (CollectionKind, EntityScope) -> PullListener,
  private val pushWorker: PushWorker,
  private val storeFactory: EntityStoreFactory,
  private val syncPreferences: SyncPreferences,
  private val ioContext: CoroutineContext,
  private val db: WingsLogDatabase? = null,
  private val uploadScheduler: UploadScheduler? = null,
) {

  private val log = Logger.withTag(TAG)

  private val supervisor = SupervisorJob()
  private val rootScope = CoroutineScope(supervisor + ioContext)

  /** Scope spun up per signed-in user; cancelled when the user changes or signs out. */
  private var userScope: CoroutineScope? = null

  /**
   * Active failure entries keyed by source: each `(kind, scope)` pair has its own entry for
   * hydration, and a single [PUSH_FAILURE_KEY] entry covers all push failures (push isn't
   * per-scope). Cleared as each source recovers. The public [failureState] surfaces the most
   * recent entry for a banner.
   */
  private val failures = MutableStateFlow<Map<Any, SyncFailure>>(emptyMap())

  /** `null` when sync is healthy. The most recent unresolved failure otherwise. */
  val failureState: StateFlow<SyncFailure?> =
    MutableStateFlow<SyncFailure?>(null).also { state ->
      rootScope.launch { failures.collect { state.value = it.values.lastOrNull() } }
    }.asStateFlow()

  /**
   * Counters for the hydration UI. `total` is the number of `(kind, scope)` pairs that have
   * needed hydration in this signed-in session; `completed` is how many finished. Reset on
   * sign-out / user change. Translated to [HydrationState] in [hydrationState].
   */
  private data class HydrationCounters(
    val completed: Int,
    val total: Int,
  )

  private val hydrationCounters = MutableStateFlow(
    HydrationCounters(
      0,
      0
    )
  )

  val hydrationState: StateFlow<HydrationState> =
    MutableStateFlow<HydrationState>(HydrationState.Idle).also { state ->
      rootScope.launch {
        hydrationCounters.collect { c ->
          state.value = when {
            c.total == 0 -> HydrationState.Idle
            c.completed < c.total -> HydrationState.InProgress(
              c.completed,
              c.total
            )

            else -> HydrationState.Done
          }
        }
      }
    }.asStateFlow()

  /**
   * Idempotent. Safe to call from app startup. The returned [Job] cancels the engine and tears
   * down all per-user work.
   */
  fun start(): Job {
    return rootScope.launch {
      combine(
        auth.authStateChanged,
        syncPreferences.state,
      ) { user, prefs -> user to prefs.cloudSyncEnabled }
        .collectLatest { (user, cloudSyncEnabled) ->
          userScope?.cancel()
          userScope = null
          hydrationCounters.value = HydrationCounters(
            0,
            0
          )
          when {
            user == null || user.isAnonymous -> {
              log.i { "auth state: signed out (or anonymous); sync idle" }
              uploadScheduler?.cancelAll()
            }

            !cloudSyncEnabled -> {
              log.i { "auth state: signed in as ${user.uid} but sync disabled by preference; idle" }
              uploadScheduler?.cancelAll()
            }

            else -> {
              log.i { "auth state: signed in as ${user.uid}; starting sync" }
              val scope = CoroutineScope(SupervisorJob(supervisor) + ioContext)
              userScope = scope
              runForUser(
                user,
                scope
              )
            }
          }
        }
    }
  }

  fun stop() {
    rootScope.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun runForUser(
    user: FirebaseUser,
    scope: CoroutineScope,
  ) {
    val uid = user.uid
    val userRoot = EntityScope.userRoot(uid)

    pushWorker.failureSink = { failure ->
      failures.update {
        if (failure == null) it - PUSH_FAILURE_KEY else it + (PUSH_FAILURE_KEY to failure)
      }
    }
    scope.launch { pushWorker.run(uid) }

    if (uploadScheduler != null && db != null) {
      scope.launch { schedulePendingBlobs(uid, uploadScheduler, db) }
    }

    for (kind in TOP_LEVEL_KINDS) {
      scope.launch {
        hydrateAndListen(
          uid,
          kind,
          userRoot
        )
      }
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
            val acScope = EntityScope.aircraftChild(
              uid,
              aircraftId
            )
            for (kind in PER_AIRCRAFT_KINDS) {
              subScope.launch {
                hydrateAndListen(
                  uid,
                  kind,
                  acScope
                )
              }
            }
          }
        }
    }
  }

  /** Per-cycle supervisor for the aircraft-child listeners; recreated each time the id-set changes. */
  private var aircraftSubScopeSupervisor: Job = Job().apply { complete() }

  /**
   * Retries hydration with exponential backoff sourced from [SyncCursorStore.recordFailure]'s
   * accumulated `failed_attempts`. Each scope's loop runs in its own coroutine, so a failing
   * collection doesn't block hydration of any other.
   */
  private suspend fun hydrateWithBackoff(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
  ) {
    while (true) {
      val attempts = cursors.get(
        uid,
        kind,
        scope
      )?.failedAttempts ?: 0
      val wait = backoffMs(attempts)
      if (wait > 0) {
        log.i { "hydration backoff ${kind.wireName} ${scope.toPath()}: ${wait}ms after $attempts attempts" }
        delay(wait)
      }
      val key: Any = kind to scope
      if (hydrationRunner.runFor(
          uid,
          kind,
          scope
        )
      ) {
        failures.update { it - key }
        return
      }
      val attemptsAfter = cursors.get(
        uid,
        kind,
        scope
      )?.failedAttempts ?: (attempts + 1)
      failures.update {
        it + (key to SyncFailure.Hydration(
          kind,
          scope,
          attemptsAfter,
          cause = null
        ))
      }
    }
  }

  private suspend fun hydrateAndListen(
    uid: String,
    kind: CollectionKind,
    scope: EntityScope,
  ) {
    if (cursors.get(
        uid,
        kind,
        scope
      )?.hydrated != true
    ) {
      hydrationCounters.update { it.copy(total = it.total + 1) }
      hydrateWithBackoff(
        uid,
        kind,
        scope
      )
      hydrationCounters.update { it.copy(completed = it.completed + 1) }
    }
    val watermark = cursors.get(
      uid,
      kind,
      scope
    )?.lastSeenRemote
    val listener = pullListenerFactory(
      kind,
      scope
    )
    pullSubscription.observe(
      kind,
      scope,
      watermark
    ).collect { remotes ->
      if (remotes.isEmpty()) return@collect
      var maxTs = Long.MIN_VALUE
      for (remote in remotes) {
        val applied = listener.apply(remote)
        if (applied > maxTs) maxTs = applied
      }
      if (maxTs != Long.MIN_VALUE) {
        cursors.advanceLastSeen(
          uid,
          kind,
          scope,
          maxTs
        )
      }
    }
  }

  private fun schedulePendingBlobs(
    uid: String,
    scheduler: UploadScheduler,
    database: WingsLogDatabase,
  ) {
    val prefix = "/users/$uid/%"
    database.schemaQueries.selectPendingUploads(scopePrefix = prefix, limit = 500)
      .executeAsList()
      .forEach { row -> scheduler.scheduleUpload(BlobId(row.id)) }
    database.schemaQueries.selectPendingDownloads(scopePrefix = prefix, limit = 500)
      .executeAsList()
      .forEach { row -> scheduler.scheduleDownload(BlobId(row.id)) }
    database.schemaQueries.selectBlobTombstones(limit = 500)
      .executeAsList()
      .forEach { row -> scheduler.scheduleDelete(BlobId(row.id)) }
  }

  companion object {
    private const val TAG = "SyncEngine"

    /** Sentinel key for push-class entries in the [failures] map (push isn't per-scope). */
    private val PUSH_FAILURE_KEY = Any()

    /** Collections that live at `users/{uid}/<wire>/...`. Hydrated on sign-in. */
    private val TOP_LEVEL_KINDS: List<CollectionKind> = listOf(
      CollectionKind.Aircraft,
      CollectionKind.Technician,
      CollectionKind.UserInfo,
    )

    /** Collections nested under `users/{uid}/aircraft/{ac}/<wire>/...`. Hydrated per aircraft. */
    private val PER_AIRCRAFT_KINDS: List<CollectionKind> = listOf(
      CollectionKind.MaintenanceLog,
      CollectionKind.MaintenanceTask,
      CollectionKind.MaintenanceOverview,
    )
  }
}
