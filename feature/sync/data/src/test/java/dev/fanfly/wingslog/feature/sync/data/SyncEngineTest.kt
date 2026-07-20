package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityCodecRegistry
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.WireCodec
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

private const val MEMBER = "member-eng-001"
private const val HOST = "host-eng-001"
private const val SHARED_AC = "ac-shared-eng"

/**
 * Engine-level behaviours of the shared-aircraft sync path (docs/sharing §10). The per-scope
 * collaborators (push-prefix widening, doc-level LWW, janitor purge, PERMISSION_DENIED
 * classification) are covered by their own tests; this drives the whole [SyncEngine] with a fake
 * pull subscription to assert the fan-out it orchestrates: refs → spin-up, ref removal → teardown,
 * and PERMISSION_DENIED → local revoke without an auth banner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

  private val ioContext = UnconfinedTestDispatcher()
  private lateinit var db: WingsLogDatabase
  private lateinit var writeLock: DatabaseWriteLock
  private lateinit var storeFactory: EntityStoreFactory
  private lateinit var pull: FakePullSubscription
  private lateinit var engine: SyncEngine

  private val sharedDocKey =
    CollectionKind.Aircraft to EntityScope.userRoot(HOST)
      .toPath()
  private val sharedNestedKey =
    CollectionKind.MaintenanceLog to EntityScope.aircraftChildUnsafe(HOST, SHARED_AC)
      .toPath()

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    writeLock = DatabaseWriteLock()
    val codecs = EntityCodecRegistry().apply {
      register(CollectionKind.Aircraft, WireCodec(Aircraft.ADAPTER))
      register(
        CollectionKind.SharedAircraftRef,
        WireCodec(SharedAircraftRef.ADAPTER)
      )
    }
    storeFactory =
      EntityStoreFactory(
        db = db,
        codecs = codecs,
        ioContext = ioContext,
        writeLock = writeLock
      )
    pull = FakePullSubscription()
    engine = buildEngine()
  }

  @Test
  fun sharedRef_spinsUpDocAndNestedListeners() = runTest(ioContext) {
    seedRef()

    val job = engine.start()
    testScheduler.advanceUntilIdle()

    // The shared aircraft doc is watched doc-level at the host's user-root; its nested maintenance
    // kinds are listened at the foreign aircraft-child scope.
    assertThat(pull.active.value).contains(sharedDocKey)
    assertThat(pull.active.value).contains(sharedNestedKey)

    job.cancel()
  }

  @Test
  fun removingRef_tearsDownSharedListeners() = runTest(ioContext) {
    seedRef()
    val job = engine.start()
    testScheduler.advanceUntilIdle()
    assertThat(pull.active.value).contains(sharedDocKey)

    // The share ends: the ref tombstone lands locally, dropping it from the live-refs set.
    refStore().delete(SHARED_AC, EntityScope.userRoot(MEMBER))
    testScheduler.advanceUntilIdle()

    assertThat(pull.active.value).doesNotContain(sharedDocKey)
    assertThat(pull.active.value).doesNotContain(sharedNestedKey)

    job.cancel()
  }

  @Test
  fun permissionDeniedOnSharedDoc_revokesLocallyAndDoesNotBanner() =
    runTest(ioContext) {
      seedRef()
      // Local copy of the shared aircraft (doc + nested log) that must be purged once revoked.
      seedEntity(CollectionKind.Aircraft, EntityScope.userRoot(HOST), SHARED_AC)
      seedEntity(
        CollectionKind.MaintenanceLog,
        EntityScope.aircraftChildUnsafe(HOST, SHARED_AC),
        "log-1"
      )
      // Hydration (mocked out here) is what writes this in production; it is the janitor's evidence
      // that these foreign-scoped rows were pulled as a share and are therefore ours to purge.
      seedSharedCursor()
      // Revocation raced the ref tombstone: the doc-level watch is denied.
      pull.denyScopes += EntityScope.userRoot(HOST)
        .toPath()

      val job = engine.start()
      testScheduler.advanceUntilIdle()

      // Stale ref hard-deleted → dropped from the store.
      assertThat(
        refStore().observeAll(EntityScope.userRoot(MEMBER))
          .first()
      ).isEmpty()
      // Janitor purged the now-orphaned shared data (doc + nested).
      assertThat(
        rowsAt(
          CollectionKind.Aircraft,
          EntityScope.userRoot(HOST)
        )
      ).isEmpty()
      assertThat(
        rowsAt(
          CollectionKind.MaintenanceLog,
          EntityScope.aircraftChildUnsafe(HOST, SHARED_AC)
        )
      )
        .isEmpty()
      // PERMISSION_DENIED was reconciled as a revoke, not surfaced as an auth banner.
      assertThat(engine.failureState.value).isNull()

      job.cancel()
    }

  @Test
  fun schedulePendingBlobs_widensToSharedAircraftScope() = runTest(ioContext) {
    seedRef()
    // A member's own pending upload, and one on the shared aircraft (host's tree). The old
    // own-tree-only scan (`/users/{member}/%`) would have found the first and stranded the second.
    seedPendingUpload(EntityScope.aircraftChildUnsafe(MEMBER, "ac-own"), "blob-own")
    seedPendingUpload(EntityScope.aircraftChildUnsafe(HOST, SHARED_AC), "blob-shared")
    // A pending blob in an unrelated user's tree that is NOT a share of ours must stay untouched —
    // scanning it would push it under our auth (PERMISSION_DENIED). Guards against over-widening.
    seedPendingUpload(EntityScope.aircraftChildUnsafe("stranger-uid", "ac-x"), "blob-foreign")
    val scheduler = FakeUploadScheduler()

    engine = buildEngine(scheduler)
    val job = engine.start()
    testScheduler.advanceUntilIdle()

    assertThat(scheduler.uploads).containsExactly("blob-own", "blob-shared")

    job.cancel()
  }

  // --- harness ---------------------------------------------------------------------------------

  private fun buildEngine(scheduler: UploadScheduler? = null): SyncEngine {
    val user = mockk<FirebaseUser> {
      every { uid } returns MEMBER
      every { isAnonymous } returns false
    }
    val auth = mockk<FirebaseAuth> {
      every { authStateChanged } returns flowOf(user)
      every { currentUser } returns user
    }
    val prefs = mockk<SyncPreferences> {
      every { state } returns MutableStateFlow(SyncPrefs(cloudSyncEnabled = true))
    }
    val hydration = mockk<HydrationRunner> {
      coEvery { runFor(any(), any(), any()) } returns true
    }
    // A no-op push worker: run() returns immediately (relaxed), which is fine — these tests exercise
    // the pull/refs fan-out, not the push queue.
    val pushWorker = mockk<PushWorker>(relaxed = true)
    return SyncEngine(
      auth = auth,
      cursors = SyncCursorStore(db, writeLock),
      pullSubscription = pull,
      hydrationRunner = hydration,
      pullListenerFactory = { kind, scope ->
        PullListener(
          kind,
          scope,
          db,
          writeLock
        )
      },
      pushWorker = pushWorker,
      storeFactory = storeFactory,
      syncPreferences = prefs,
      ioContext = ioContext,
      db = db,
      uploadScheduler = scheduler,
      sharedScopeJanitor = SharedScopeJanitor(db, writeLock),
      writeLock = writeLock,
    )
  }

  private fun refStore() =
    storeFactory.create<SharedAircraftRef>(CollectionKind.SharedAircraftRef)

  private suspend fun seedRef() {
    refStore().put(
      SHARED_AC,
      SharedAircraftRef(aircraft_id = SHARED_AC, host_uid = HOST),
      EntityScope.userRoot(MEMBER),
    )
  }

  private suspend fun seedEntity(
    kind: CollectionKind,
    scope: EntityScope,
    id: String
  ) {
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope.toPath(),
      id = id,
      payload = byteArrayOf(0x01),
      payload_schema = kind.schemaName,
      updated_at = 1000L,
      remote_updated_at = null,
      dirty = false,
      deleted = false,
      writer_uid = null,
    )
  }

  private suspend fun seedSharedCursor() {
    db.schemaQueries.upsertCursor(
      uid = MEMBER,
      collection = CollectionKind.MaintenanceLog,
      scope_path = EntityScope.aircraftChildUnsafe(HOST, SHARED_AC)
        .toPath(),
      hydrated = true,
      last_seen_remote = null,
      failed_attempts = 0L,
      last_attempt_at = null,
    )
  }

  private suspend fun seedPendingUpload(scope: EntityScope, blobId: String) {
    db.schemaQueries.upsertBlob(
      id = blobId,
      scope_path = scope.toPath(),
      relative_path = "blobs/$blobId",
      content_type = "image/jpeg",
      size_bytes = 1L,
      sha256 = "sha-$blobId",
      remote_state = RemoteState.LocalOnly,
      remote_path = null,
      upload_attempts = 0L,
      last_attempt_at = null,
      updated_at = 1000L,
      deleted = false,
    )
  }

  private fun rowsAt(kind: CollectionKind, scope: EntityScope) =
    db.schemaQueries.selectAll(kind, scope.toPath())
      .executeAsList()
}

/** Records the blob ids handed to each schedule call so tests can assert what the scan enqueued. */
private class FakeUploadScheduler : UploadScheduler {
  val uploads = mutableListOf<String>()
  val downloads = mutableListOf<String>()
  val deletes = mutableListOf<String>()

  override fun scheduleUpload(blobId: BlobId) { uploads += blobId.value }
  override fun scheduleDownload(blobId: BlobId) { downloads += blobId.value }
  override fun scheduleDelete(blobId: BlobId) { deletes += blobId.value }
  override fun cancelAll() {}
}

/**
 * Records which `(kind, scopePath)` pairs are currently being collected so tests can assert
 * spin-up/teardown, and can be told to fail a scope's subscription with PERMISSION_DENIED.
 */
private class FakePullSubscription : PullSubscription {
  /** `(kind, scopePath)` currently subscribed (collecting). */
  val active = MutableStateFlow<Set<Pair<CollectionKind, String>>>(emptySet())

  /** Scope paths whose subscription throws PERMISSION_DENIED on collect. */
  val denyScopes = mutableSetOf<String>()

  private fun track(
    kind: CollectionKind,
    scopePath: String
  ): Flow<List<RemoteEntity>> = flow {
    if (scopePath in denyScopes) throw permissionDenied()
    active.update { it + (kind to scopePath) }
    try {
      awaitCancellation()
    } finally {
      active.update { it - (kind to scopePath) }
    }
  }

  override fun observeCollection(
    kind: CollectionKind,
    scope: EntityScope,
    sinceRemoteTsMs: Long?,
  ): Flow<List<RemoteEntity>> = track(kind, scope.toPath())

  override fun observeSingleDoc(
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
  ): Flow<List<RemoteEntity>> = track(kind, scope.toPath())
}

private fun permissionDenied(): FirebaseFirestoreException =
  mockk<FirebaseFirestoreException>(relaxed = true) {
    every { code } returns FirestoreExceptionCode.PERMISSION_DENIED
  }
