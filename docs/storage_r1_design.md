# R1 Design: Local-First Protos with Cloud Sync

**Branch:** `r1-storage-design`
**PRD:** [`storage_mode_PRD.md`](storage_mode_PRD.md) — §11.1 R1
**Scope:** M1 + M2 + M3 + R1-portion of M6
**Out of scope (R2):** attachment binaries — they continue uploading directly to Firebase Storage; the URL is stored inside the owning proto's bytes.

---

## 1. Goals & non-goals for this release

**In:**
- Every domain proto (Aircraft, MaintenanceTask, MaintenanceLog, MaintenanceOverview, Technician, UserProfile) is read from and written to a local SQLite store first.
- A sync engine pushes local changes to Firestore and pulls remote changes back, transparently.
- The UI's reactive read path no longer touches Firestore directly — it observes SQLDelight.
- Anonymous (guest) users get a fully working app for all proto-backed screens.
- A user-controlled "Enable Sync" toggle in Settings, gated on `isAnonymous == false`.

**Out:**
- Attachment binaries remain on Firebase Storage. `AttachmentManager` is **untouched** in R1.
- No CRDT, no per-field merging — last-writer-wins on Firestore server timestamp.
- No "remove account from this device" wipe (defer to R2/M6 polish).

---

## 2. Module layout

Two new gradle modules. Both are KMM library modules under `core/`.

```
core/
  storage/                                  # M1
    build.gradle.kts                        #   sqldelight plugin, multiplatform, koin, kermit, wire-runtime
    src/commonMain/sqldelight/
      dev/fanfly/wingslog/core/storage/db/
        Schema.sq                           #   tables, indexes (see §4)
    src/commonMain/kotlin/.../core/storage/
      CollectionKind.kt                     #   sealed interface + ColumnAdapter
      EntityScope.kt
      EntityCodec.kt
      EntityCodecRegistry.kt
      EntityStore.kt                        #   interface
      StorageEntity.kt
      di/StorageModule.kt
      impl/
        SqlDelightEntityStore.kt
        DriverFactory.kt                    #   expect class
    src/androidMain/kotlin/.../impl/
      DriverFactory.android.kt              #   AndroidSqliteDriver(context, "wingslog.db")
    src/iosMain/kotlin/.../impl/
      DriverFactory.ios.kt                  #   NativeSqliteDriver(schema, "wingslog.db")

  sync/                                     # M3
    src/commonMain/kotlin/.../core/sync/
      SyncEngine.kt
      PushWorker.kt
      PullListener.kt
      HydrationRunner.kt
      SyncCursorStore.kt
      SyncPreferences.kt                    #   wraps Multiplatform Settings
      di/SyncModule.kt
```

`composeApp/di/initKoin.kt` adds `storageModule` and `syncModule` to the aggregate list.

---

## 3. Public Kotlin API

### 3.1 `CollectionKind`

```kotlin
sealed interface CollectionKind {
  val wireName: String
  val schemaName: String   // e.g. "aircraft.Aircraft" — for the payload_schema column

  data object Aircraft            : CollectionKind { override val wireName = "aircraft";             override val schemaName = "aircraft.Aircraft" }
  data object MaintenanceTask     : CollectionKind { override val wireName = "maintenance_task";     override val schemaName = "aircraft.MaintenanceTask" }
  data object MaintenanceLog      : CollectionKind { override val wireName = "maintenance_log";      override val schemaName = "aircraft.MaintenanceLog" }
  data object MaintenanceOverview : CollectionKind { override val wireName = "maintenance_overview"; override val schemaName = "aircraft.MaintenanceOverview" }
  data object Technician          : CollectionKind { override val wireName = "technician";           override val schemaName = "aircraft.Technician" }
  data object UserProfile         : CollectionKind { override val wireName = "user_profile";         override val schemaName = "userprofile.UserProfile" }

  companion object {
    private val byWire = listOf(Aircraft, MaintenanceTask, MaintenanceLog, MaintenanceOverview, Technician, UserProfile)
      .associateBy { it.wireName }
    fun fromWire(s: String): CollectionKind =
      byWire[s] ?: error("Unknown collection '$s' — register in CollectionKind")
  }
}

// SQLDelight ColumnAdapter
val collectionKindAdapter = object : ColumnAdapter<CollectionKind, String> {
  override fun decode(databaseValue: String) = CollectionKind.fromWire(databaseValue)
  override fun encode(value: CollectionKind) = value.wireName
}
```

### 3.2 `EntityScope`

The path that disambiguates collections for nested data (e.g. maintenance logs live under an aircraft).

```kotlin
@JvmInline value class EntityScope(val segments: List<String>) {
  fun toPath(): String = segments.joinToString(separator = "/", prefix = "/", postfix = "/")

  companion object {
    fun userRoot(uid: String) = EntityScope(listOf("users", uid))
    fun aircraftChild(uid: String, aircraftId: String) =
      EntityScope(listOf("users", uid, "aircraft", aircraftId))
  }
}
```

Mapping from feature to scope:

| Collection | Scope |
|---|---|
| Aircraft | `userRoot(uid)` |
| Technician | `userRoot(uid)` |
| UserProfile | `userRoot(uid)` (single doc, fixed id) |
| MaintenanceTask | `aircraftChild(uid, aircraftId)` |
| MaintenanceLog | `aircraftChild(uid, aircraftId)` |
| MaintenanceOverview | `aircraftChild(uid, aircraftId)` (single doc, fixed id) |

### 3.3 `EntityCodec`

```kotlin
interface EntityCodec<T : Any> {
  fun encode(value: T): ByteArray
  fun decode(bytes: ByteArray): T
}

// Wire-backed codec; one per proto type.
class WireCodec<T : Message<*, *>>(private val adapter: ProtoAdapter<T>) : EntityCodec<T> {
  override fun encode(value: T) = adapter.encode(value)
  override fun decode(bytes: ByteArray) = adapter.decode(bytes)
}
```

Registration happens in `StorageModule`:
```kotlin
single { EntityCodecRegistry().apply {
  register(CollectionKind.Aircraft,            WireCodec(Aircraft.ADAPTER))
  register(CollectionKind.MaintenanceTask,     WireCodec(MaintenanceTask.ADAPTER))
  register(CollectionKind.MaintenanceLog,      WireCodec(MaintenanceLog.ADAPTER))
  register(CollectionKind.MaintenanceOverview, WireCodec(MaintenanceOverview.ADAPTER))
  register(CollectionKind.Technician,          WireCodec(Technician.ADAPTER))
  register(CollectionKind.UserProfile,         WireCodec(UserProfile.ADAPTER))
}}
```

A startup assertion in `StorageModule.start()` verifies every `CollectionKind` enum case has a registered codec — fail fast on developer error.

### 3.4 `EntityStore`

```kotlin
interface EntityStore<T : Any> {
  val kind: CollectionKind
  fun observeAll(scope: EntityScope): Flow<List<StorageEntity<T>>>
  fun observe(id: String, scope: EntityScope): Flow<StorageEntity<T>?>
  suspend fun put(id: String, value: T, scope: EntityScope)
  suspend fun delete(id: String, scope: EntityScope)
}

data class StorageEntity<T>(val id: String, val value: T, val updatedAt: Instant)
```

Behavior contract:
- `put` upserts with `dirty = 1`, `updated_at = Clock.System.now()`. Emits to `observeAll`/`observe` flows synchronously.
- `delete` upserts a tombstone (`deleted = 1, dirty = 1`); flows emit a list with the row absent.
- `observeAll` returns rows where `deleted = 0`, ordered by `updated_at DESC` (overridable via SQL view).
- The store knows nothing about Firestore. The sync engine does the rest.

---

## 4. SQLDelight schema (`Schema.sq`)

```sql
CREATE TABLE entity (
  collection         TEXT    AS CollectionKind NOT NULL,
  scope_path         TEXT    NOT NULL,
  id                 TEXT    NOT NULL,
  payload            BLOB    NOT NULL,
  payload_schema     TEXT    NOT NULL,
  updated_at         INTEGER AS Long NOT NULL,
  remote_updated_at  INTEGER AS Long,
  dirty              INTEGER AS Boolean NOT NULL DEFAULT 0,
  deleted            INTEGER AS Boolean NOT NULL DEFAULT 0,
  PRIMARY KEY (collection, scope_path, id)
);
CREATE INDEX entity_scope_idx  ON entity(collection, scope_path);
CREATE INDEX entity_dirty_idx  ON entity(collection, scope_path, updated_at) WHERE dirty = 1;

CREATE TABLE sync_cursor (
  uid              TEXT NOT NULL,
  collection       TEXT AS CollectionKind NOT NULL,
  scope_path       TEXT NOT NULL,
  hydrated         INTEGER AS Boolean NOT NULL DEFAULT 0,
  last_seen_remote INTEGER AS Long,                       -- max remote_updated_at observed
  PRIMARY KEY (uid, collection, scope_path)
);

-- Generated queries (selected):

observeAll:
SELECT id, payload, updated_at FROM entity
WHERE collection = :collection AND scope_path = :scope AND deleted = 0
ORDER BY updated_at DESC;

observeOne:
SELECT id, payload, updated_at FROM entity
WHERE collection = :collection AND scope_path = :scope AND id = :id AND deleted = 0;

upsert:
INSERT OR REPLACE INTO entity(collection, scope_path, id, payload, payload_schema, updated_at, remote_updated_at, dirty, deleted)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

selectDirty:
SELECT collection, scope_path, id, payload, updated_at, deleted FROM entity
WHERE dirty = 1 ORDER BY updated_at ASC LIMIT :limit;

clearDirty:
UPDATE entity SET dirty = 0, remote_updated_at = :remoteTs
WHERE collection = ? AND scope_path = ? AND id = ?;

-- Cursor:
upsertCursor:
INSERT OR REPLACE INTO sync_cursor VALUES (?, ?, ?, ?, ?);
selectCursor:
SELECT * FROM sync_cursor WHERE uid = ? AND collection = ? AND scope_path = ?;
```

Migrations directory: `src/commonMain/sqldelight/databases/`. The initial schema is the v1 migration.

**Note:** `blob_object` table from the PRD is **deferred to R2**. R1 doesn't introduce it. R1's `entity` table is sufficient because attachments are referenced by URL inside proto payloads, and those proto bytes ride the same sync path as everything else.

---

## 5. Sync engine internals (M3)

### 5.1 Lifecycle and scope

`SyncEngine` is a singleton owned by Koin, keyed to the application lifecycle. It exposes:

```kotlin
class SyncEngine(
  private val authManager: AuthManager,
  private val syncPrefs: SyncPreferences,
  private val push: PushWorker,
  private val pulls: List<PullListener>,
  private val hydration: HydrationRunner,
) {
  fun start()   // called from MainActivity / iOS app delegate after auth resolves
  fun stop()    // called on sign-out or sync-disabled
}
```

`start()` checks: `currentUser != null && !currentUser.isAnonymous && syncPrefs.isEnabled(uid)`. If any condition fails, the engine stays idle.

When all conditions are met:
1. For each `(CollectionKind, EntityScope)` pair the user owns, look up `sync_cursor`. If `hydrated = 0`, queue a `HydrationRunner.runFor(kind, scope)` job.
2. After hydration, attach a `PullListener` filtered by `where("updated_at", ">", cursor.last_seen_remote)`.
3. Start `PushWorker` collecting on `selectDirty` change notifications.

`stop()` cancels the engine's `CoroutineScope`. Listeners detach. PushWorker stops mid-iteration; partially-pushed rows stay `dirty = 1` (durable).

### 5.2 PushWorker

```kotlin
class PushWorker(
  private val db: WingsLogDatabase,
  private val firestore: FirebaseFirestore,
  private val codecs: EntityCodecRegistry,
) {
  suspend fun run(scope: CoroutineScope) {
    db.entityQueries.observeDirtyCount().asFlow().mapToOne(scope.coroutineContext)
      .filter { it > 0L }
      .collect { drain() }
  }

  private suspend fun drain() {
    while (true) {
      val batch = db.entityQueries.selectDirty(limit = 50).executeAsList()
      if (batch.isEmpty()) return
      for (row in batch) pushOne(row)
    }
  }

  private suspend fun pushOne(row: DirtyRow) {
    val docRef = firestoreDocRef(row.collection, row.scope_path, row.id)
    val data = mapOf(
      "payload"    to FirestoreBlob(row.payload),
      "deleted"    to row.deleted,
      "updated_at" to FieldValue.serverTimestamp(),
      "schema"     to row.collection.schemaName,
    )
    runCatching { docRef.set(data, merge = false) }
      .onSuccess {
        val ackTs = docRef.get()["updated_at"] as Long  // server ts after ack
        db.entityQueries.clearDirty(row.collection, row.scope_path, row.id, remoteTs = ackTs)
      }
      .onFailure { e ->
        when (e) {
          is FirebaseNetworkException -> backoff(currentBackoff())
          is FirebaseAuthException     -> stopWithBanner("Authentication expired")
          else                          -> stopWithBanner("Sync error: ${e.message}")
        }
      }
  }
}
```

### 5.3 PullListener

One per `(CollectionKind, EntityScope)`. Wraps a Firestore snapshot listener.

```kotlin
class PullListener(
  private val kind: CollectionKind,
  private val scope: EntityScope,
  private val db: WingsLogDatabase,
  private val firestore: FirebaseFirestore,
) {
  fun attach(coroutineScope: CoroutineScope, sinceRemoteTs: Long?) = coroutineScope.launch {
    val query = firestoreCollectionRef(kind, scope)
      .orderBy("updated_at", Direction.ASCENDING)
      .let { if (sinceRemoteTs != null) it.where("updated_at", ">", sinceRemoteTs) else it }

    query.snapshots.collect { snap ->
      for (change in snap.documentChanges) {
        applyRemote(change.document)
      }
    }
  }

  private fun applyRemote(doc: DocumentSnapshot) {
    val remoteTs = doc.get<Long>("updated_at")
    val payload  = doc.get<FirestoreBlob>("payload").toByteArray()
    val deleted  = doc.get<Boolean>("deleted") ?: false

    val local = db.entityQueries.observeOne(kind, scope.toPath(), doc.id).executeAsOneOrNull()
    when {
      local == null -> upsertFromRemote(payload, deleted, remoteTs)
      local.dirty && local.updated_at > remoteTs -> { /* skip — our pending push wins */ }
      remoteTs > (local.remote_updated_at ?: 0L) -> upsertFromRemote(payload, deleted, remoteTs)
      else -> { /* up to date */ }
    }
    db.syncCursorQueries.upsertCursor(uid, kind, scope.toPath(), hydrated = true, last_seen_remote = remoteTs)
  }
}
```

### 5.4 HydrationRunner

```kotlin
suspend fun runFor(kind: CollectionKind, scope: EntityScope) {
  val ref = firestoreCollectionRef(kind, scope)
  val docs = ref.get()                       // one-shot
  db.transaction {
    for (doc in docs.documents) {
      db.entityQueries.upsert(
        collection = kind, scope_path = scope.toPath(), id = doc.id,
        payload = doc.get<FirestoreBlob>("payload").toByteArray(),
        payload_schema = kind.schemaName,
        updated_at = doc.get<Long>("updated_at"),
        remote_updated_at = doc.get<Long>("updated_at"),
        dirty = false, deleted = doc.get<Boolean>("deleted") ?: false,
      )
    }
    val maxTs = docs.documents.maxOfOrNull { it.get<Long>("updated_at") } ?: 0L
    db.syncCursorQueries.upsertCursor(uid, kind, scope.toPath(), hydrated = true, last_seen_remote = maxTs)
  }
}
```

For nested collections (logs/tasks/overview under an aircraft), hydration walks the parent first, then recurses. Practically: hydrate Aircraft, then for each aircraft id hydrate its three subcollections.

Hydration progress is reported via a `StateFlow<HydrationState>` so the UI can show "Restoring 3 of 6 collections…" on first launch.

---

## 6. Manager refactor (M2)

Each manager becomes a thin shim over `EntityStore<T>` plus business logic. Worked example with `FleetManager`:

```kotlin
class FleetManagerImpl(
  private val authManager: AuthManager,
  private val store: EntityStore<Aircraft>,
) : FleetManager {

  private fun scope(): EntityScope =
    EntityScope.userRoot(authManager.requireUid())

  override fun observeFleetDashboard(): Flow<List<Aircraft>> =
    store.observeAll(scope()).map { rows -> rows.map { it.value } }

  override fun loadAircraft(id: String): Flow<Aircraft?> =
    store.observe(id, scope()).map { it?.value }

  override suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean> = runCatching {
    val withId = if (aircraft.id.isEmpty()) aircraft.copy(id = randomId()) else aircraft
    store.put(withId.id, withId, scope())
    true
  }

  override suspend fun deleteAircraft(id: String): Result<Boolean> = runCatching {
    store.delete(id, scope()); true
  }
}
```

The Firestore-specific imports disappear from the manager. The Koin module for fleet now binds `EntityStore<Aircraft>` from `core/storage`:

```kotlin
val fleetDataManagerModule = module {
  single<EntityStore<Aircraft>> { get<EntityStoreFactory>().create(CollectionKind.Aircraft) }
  single<FleetManager> { FleetManagerImpl(get(), get()) }
}
```

`EntityStoreFactory.create(kind)` returns an `EntityStore<T>` parameterized by the codec registered for `kind`. (The factory shape avoids per-type Koin singletons for everything in `core/storage`.)

### 6.1 Per-manager refactor checklist

| Manager | Collection | Scope source | Notes |
|---|---|---|---|
| `FleetManager` (Aircraft) | `Aircraft` | `userRoot(uid)` | Worked example above. |
| `MaintenanceLogManager` | `MaintenanceLog`, `MaintenanceOverview` | `aircraftChild(uid, aircraftId)` | Two stores injected: logs + overview. |
| `TaskDataManager` | `MaintenanceTask` | `aircraftChild(uid, aircraftId)` | |
| `TechnicianManager` | `Technician` | `userRoot(uid)` | |
| `UserProfileManager` | `UserProfile` | `userRoot(uid)` | Fixed id `"profile"`. |
| `TaskDueManager` | derived | n/a | Pure computation over other stores; no migration needed. |

Each manager gets a unit test (`kmm-test-writer`) verifying `Flow` emissions on `put`/`delete` round-trips.

### 6.2 Removing Firestore from managers

After M2, `core/database` is reduced to:
- `generateRandomId()` (still useful for new entity ids)
- Firestore document/collection refs **only used by `core/sync`** — the constants (`AIRCRAFT_INFO_BLOB`, paths) move to `core/sync` since that's their sole consumer.

The `expect`/`actual` `setEncoded` and `getBlobAsBytes` extensions move to `core/sync` (or are inlined into the sync engine). `core/database` becomes a placeholder that may be deleted in R2.

---

## 7. Auth integration

### 7.1 `AuthManager` adds two helpers

```kotlin
interface AuthManager {
  // existing…
  fun observeAuthState(): Flow<AuthState>           // Firebase auth state listener wrapped
  fun requireUid(): String                           // throws if not signed in — managers call this
  suspend fun linkAnonymousWithGoogle(): FirebaseUser?  // M6 R1 portion
}

sealed interface AuthState {
  data object SignedOut : AuthState                 // pre-LoginScreen
  data class Anonymous(val uid: String) : AuthState
  data class Permanent(val uid: String, val provider: String) : AuthState
}
```

### 7.2 Sync engine subscribes to `observeAuthState`

```kotlin
authManager.observeAuthState().collect { state ->
  when (state) {
    is AuthState.Permanent -> if (syncPrefs.isEnabled(state.uid)) syncEngine.start() else syncEngine.stop()
    is AuthState.Anonymous,
    is AuthState.SignedOut -> syncEngine.stop()
  }
}
```

This is the only place `isAnonymous` decides anything. Managers don't care.

### 7.3 `linkWithCredential` flow

User taps "Sign in with Google" while currently anonymous. The implementation calls `firebaseAuth.currentUser?.linkWithCredential(...)`. Because the uid is preserved:

- No SQL changes.
- `observeAuthState` emits `Permanent(sameUid, "google.com")`.
- Sync engine starts. PushWorker drains accumulated `dirty=1` rows (the user's offline work) up to Firestore. No hydration runs — `sync_cursor` for this uid has no rows yet, so hydration would run, but **the dirty queue is pushed first**, so by the time hydration's listener attaches, the cloud already has our data.

There's a subtle ordering invariant: **for a brand-new permanent uid, push must run before pull**. Otherwise a hydration that finds no docs would write nothing into local (fine), then push would publish, then a snapshot listener would echo back our own writes (also fine — they're already in local). So actually order doesn't matter; it's just less wasted bandwidth to push first. Implementation: gate `PullListener.attach()` until `PushWorker.drainOnce()` completes.

---

## 8. Settings UI (R1 portion of M6)

A single new row in `feature/settings/SettingsScreen.kt`:

```kotlin
SettingsRow(
  title = "Enable Sync",
  subtitle = if (isAnonymous) "Sign in to back up your data" else "Backed up to the cloud",
  trailing = {
    Switch(
      checked = isSyncEnabled,
      enabled = !isAnonymous,
      onCheckedChange = viewModel::onSyncToggled,
    )
  },
)
```

`SettingsViewModel` reads from `SyncPreferences` and `AuthManager.observeAuthState()`. On toggle, it writes the preference and the sync engine reacts via the auth-state collector.

A second optional row for R1 polish:
```kotlin
if (syncFailureBannerVisible) {
  SyncFailureBanner(message = "Some changes haven't synced yet", onRetry = viewModel::onRetrySync)
}
```

The banner observes `SyncEngine.failureState: StateFlow<SyncFailure?>`.

---

## 9. R1 attachment compatibility

`AttachmentManager` is **untouched** in R1. Its current behavior:
1. Picks a file → uploads to Firebase Storage.
2. Receives a download URL.
3. Stores the URL inside an `Attachment` proto field on the owning entity (e.g. `Aircraft.attachments`).

In R1, step 3's payload reaches local storage via `EntityStore<Aircraft>` like any other field. The URL string round-trips through proto bytes, the sync engine pushes those bytes to Firestore, other devices pull them, and `AttachmentOpener` resolves the URL the same way it does today.

This means in R1:
- Attachments require network to upload (unchanged).
- Anonymous users **cannot add attachments** — Firebase Storage requires auth and we don't sync their data anyway. The attachment add UI must be hidden/disabled for anonymous users in R1.
- All other proto-level operations work offline.

R2 lifts the attachment limitation.

---

## 10. Existing-user migration

Today's users have data in Firestore and an empty local DB. On first R1 launch:

1. `AuthManager` sees the cached Firebase session → `Permanent(uid)`.
2. `SyncEngine.start()` runs.
3. `sync_cursor` is empty → `HydrationRunner` pulls every collection.
4. Listeners attach.
5. UI reads from local. From the user's POV: a one-time progress sheet on first open of the new build, then steady state.

No special migration code path. This is the same code that runs on a fresh-device sign-in.

**Edge case — user has thousands of maintenance logs:** hydration is paginated by Firestore (`limit(500)` per page) and writes to SQLite in batches of 100 inside `db.transaction { … }`. Progress reported per page.

---

## 11. Testing

### 11.1 Contract tests for `EntityStore`

`core/storage` ships a `EntityStoreContract<T>` test base class that any `EntityStore<T>` implementation must pass. R1 has only the SQLDelight impl, but the base class is the spec a future in-memory or test impl must meet.

Cases:
- `put` + `observeAll` emits the row.
- `put` then `delete` → `observeAll` no longer emits, `observe(id)` emits null.
- Two scopes are isolated — putting under scope A is invisible to scope B.
- Concurrent `put`s of the same id collapse to last-write.
- `dirty` is set on every write, cleared by direct `clearDirty` call.

### 11.2 Sync engine tests (`kmm-test-writer`)

- **PushWorker:** writes row → mocked Firestore receives `set(doc, merge=false)` once → row's `dirty` flag clears.
- **PullListener:** mocked Firestore emits a doc with `updated_at = 1000` → local row has `remote_updated_at = 1000, dirty = 0`.
- **LWW conflict:** local has `dirty=1, updated_at=2000`; remote arrives with `updated_at=1500` → local row untouched.
- **LWW conflict (other direction):** local `dirty=0, remote_updated_at=1000`; remote arrives with `updated_at=2000` → local overwritten.
- **HydrationRunner:** seeds Firestore with N docs → after run, local has N rows with `dirty=0`, `sync_cursor.hydrated=true`.

### 11.3 Manager unit tests

Existing Manager tests (using MockK for Firestore) are rewritten to mock `EntityStore<T>` instead. Net simpler — `EntityStore` is a tighter interface.

### 11.4 Integration test on a single device

Spin up an emulator, sign in as a new user, write data, force-quit, restart, verify data still present. Toggle sync off, write more, toggle on, verify push drains. Sign out, sign in as different user, verify scope isolation.

---

## 12. Rollout

- Build-time flag `ENABLE_LOCAL_FIRST_R1` defaults `false` in `app/build.gradle.kts`. Internal/dogfood builds flip to `true`.
- When disabled, R1 modules are present but inert — `core/sync` doesn't start, managers fall back to the old Firestore code path. (Implementation: keep both `FleetManagerImpl` and a new `FleetManagerLocalImpl`; the Koin binding is conditional on the flag for one release.)
- Staged rollout: 10% → 50% → 100% over ~2 weeks per stage. Watch:
  - Crash-free rate (target: ≥ pre-R1 baseline).
  - Firestore read count (expect: large drop — UI no longer subscribes per-screen).
  - Firestore write count (expect: roughly flat — same writes, just batched through PushWorker).
  - "Items not synced" banner display rate (target: <0.5% of sessions).

After 100% holds for a week, the old Firestore-direct manager impls are deleted in a follow-up PR. That is the cleanup commit; no behavioral change.

---

## 13. Open questions

1. **Resilience to clock skew on `updated_at` for the LWW skip rule.** The push-skip rule uses `local.updated_at > remoteUpdatedAt` where local is wall-clock and remote is server time. Possible clock skew on the user's device could mis-skip a remote write that should win. Mitigation: store the device's last known server-clock offset on each successful push, apply it to local timestamps in the comparator. Decide before M3 ships.

2. **Should `sync_cursor.last_seen_remote` be per-doc or per-collection?** Per-collection is simpler and matches Firestore's `where("updated_at", ">", cursor)` query. Per-doc is more accurate but pays N round trips on resume. Default to per-collection; revisit if real users hit listener replay storms.

3. **Tombstone GC schedule.** PRD says "30 days." Need to decide whether GC runs on app start, on a Worker, or on every push completion. Default: app start, with a SQL `DELETE … WHERE deleted=1 AND updated_at < now() - 30d`. Cheap and predictable.

4. **Firestore offline persistence.** GitLive's KMP Firestore wrapper enables Firestore's own offline cache by default. With us managing our own queue, this is duplicate state. Decision: **disable** Firestore's offline persistence at init. Reduces confusion during debugging and saves disk.

---

## 14. Summary

R1 = three new modules (`core/storage`, `core/sync`, settings UI row), six manager refactors, one schema (`entity` + `sync_cursor`), one engine (push + pull + hydrate). Attachments stay on the existing path. Anonymous users get the app for everything except attachments. Permanent users get sync for free.

Estimated work: ~3 weeks of single-engineer effort, plus 1 week of staged rollout. Each milestone (M1, M2, M3, R1-M6) is independently testable but only ships behind the flag flipping in a single release.
