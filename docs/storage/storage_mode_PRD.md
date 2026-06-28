## PRD: Local-First Storage with Optional Cloud Sync

**Status:** R1 ✅ Shipped · R2 substantially implemented (attachment UI behind `attachmentUploadEnabled` flag)
**Owner:** @fanzhang172
**Created:** 2026-04-28
**Last updated:** 2026-05-22
**Targets:** Android (minSdk 33), iOS — Compose Multiplatform

---

> **Implementation status.** R1 (local-first protos + cloud sync, milestones M1–M3 + the R1 portion of M6) is
> shipped as the default and only data path. R2 (local-first attachments, M4–M5 + iOS background uploads) is
> substantially implemented — the `feature/attachment` module, `blob_object` store, and background
> upload/download/delete drivers all exist — but the attachment UI is gated behind the `attachmentUploadEnabled`
> feature-lab flag. See `storage_r1_design.md` and `storage_r2_design.md` for the per-design delta notes.

---

### 1. Problem & Goals

Today every SquawkIt write goes directly to Firebase Firestore, and the UI's reactive flows are wired to Firestore snapshots. This means:

- The app does not work meaningfully offline.
- Anonymous users (Firebase anonymous auth principals — they have a real uid, but it is ephemeral and device-tied) have their writes flow to Firestore under an account they cannot recover, which is the wrong default for a "kicking the tires" experience.
- Users who don't want their logbooks on a third-party server have no option.
- Real-time updates and cloud presence are coupled to the read path; you can't turn one off without breaking the other.

**Plan of record:** invert the architecture to **local-first**. The on-device store is the single source of truth for every read and write. When the user is signed in and sync is enabled, a separate sync engine pushes local changes to Firestore and pulls remote changes back into local. **Toggling sync off is non-disruptive** — the local dataset is unchanged, the UI keeps working, only the sync engine stops.

#### Goals

- One reactive read path for all features: SQLDelight `Flow` → ViewModel → Compose UI. No Firestore in the read path.
- Anonymous users get a fully functional app backed by local storage.
- Signed-in users get cloud sync by default; they can turn it off without losing data or disrupting the UI.
- Storage layout is **forward-compatible**: renaming a proto field, adding a proto type, or adding a brand-new domain (e.g. **digital signatures**) must not destroy existing rows and must not require schema migration.
- All existing feature managers (`FleetManager`, `MaintenanceLogManager`, `TechnicianManager`, `AttachmentManager`, `UserProfileManager`, `TaskDataManager`, `TaskDueManager`) keep their public Flow-based contracts. UI/ViewModels do not change.

#### Non-goals (this milestone)

- Multi-device conflict resolution beyond last-writer-wins keyed on Firestore server timestamp. SquawkIt is single-user-per-account; LWW is sufficient.
- End-to-end encryption of the local store (OS sandbox is the trust boundary for v1).
- Implementing digital signatures — only ensure the schema can absorb them later.
- A migration tool for existing cloud-only users (covered in §9).

---

### 2. User Story & UX

> As a pilot/owner, I want my logbook to live on my device so I can fly off-grid and never see a loading spinner — and I want it to sync to the cloud so I can get a new phone and not lose anything.

#### 2.1 The setting

`Settings → Sync → [Enable Sync]` (single switch). The toggle is gated on `firebaseAuth.currentUser?.isAnonymous == false`.

| Auth state | Toggle state | Default | Notes |
|---|---|---|---|
| Anonymous (Firebase anonymous principal) | **Greyed out, forced OFF** | OFF | Helper text: *"Sign in to back up your data."* The user has a uid, but it's device-tied and ephemeral; we don't sync data we'd later orphan. |
| Permanent (signed-in via Google / email / Apple / etc.) | Enabled, user-controllable | **ON** | First time on this device runs initial hydration; subsequent times resume from the saved cursor. |

**The LoginScreen is the gate.** No `EntityStore` write can happen before the user has chosen how to sign in. The flow is: app start → `AuthManager` checks Firebase's session cache → if no cached user, show LoginScreen → user picks one of:

- Sign in with Google / email / Apple / etc. → permanent uid.
- "Continue as guest" → `AuthManager.signInAnonymously()` → anonymous uid.

`signInAnonymously()` is **never automatic** — it is one user-chosen option on the LoginScreen, on equal footing with the other providers. After the user's choice, `AuthManager` exposes the uid, and from that point on every write goes to `/users/{uid}/...`. The user later moves from anonymous to permanent via `linkWithCredential` (see §7).

Behavior on toggle:

- **Off → On (permanent user):** sync engine starts, attaches Firestore snapshot listeners, push worker begins draining dirty rows. If this is the first time syncing on this device, runs **initial hydration** (one-shot pull of every collection) before listeners go live. UI keeps reading from local throughout.
- **On → Off:** sync engine cancels its scope, detaches all listeners, push/upload workers stop. Local DB is unchanged. UI is unaffected. Pending dirty rows stay dirty; if the user re-enables sync later, they drain.
- **Sign-out:** equivalent to On → Off. Sync state remembered per-uid so a different account on the same device gets its own preference.

No "Cloud vs Local" wording anywhere. The mental model is "your data lives on this device; sync is an optional service that mirrors it to the cloud."

---

### 3. Storage Choice (per-platform)

#### 3.1 Requirements

| Requirement | Implication |
|---|---|
| Store opaque proto-encoded `ByteArray` per row | Key/value-by-id, not relational |
| Reactive `Flow<List<T>>` per collection | Native change-notification |
| Hold attachment binaries up to tens of MB | Filesystem, not DB rows |
| Survive proto field rename/removal | Store the encoded proto bytes; decode at read time |
| Extensible to new domains with one schema | Generic "(collection, id, blob, metadata)" rows |
| KMM-friendly | Prefer libraries with KMP bindings |

#### 3.2 Survey

| Option | Verdict |
|---|---|
| **SQLDelight** (KMP) | **Chosen.** Mature, reactive Flow, single schema for both platforms. Most popular KMM storage by a wide margin. |
| Room KMP | Newer KMP support (stable since AndroidX Room 2.7). Less battle-tested on iOS than SQLDelight. |
| Realm KMP | Schema coupled to Kotlin classes — bad fit for proto rename resilience. |
| Proto DataStore | Android-only; whole-file rewrite per change is wrong shape for many rows. Used **only** for the small "sync enabled" preference on Android. |
| Plain files / per-proto file | No reactive layer. Used **only** for attachment binaries. |

#### 3.3 Decision

- **Primary store: SQLDelight** with one generic schema (see §4.2). Proto bytes stored opaquely in `BLOB` columns.
- **Attachment binaries: filesystem** under the app private directory (`Context.filesDir` on Android, `NSDocumentDirectory` on iOS). DB row holds path + metadata.
- **Sync-enabled preference:** **Multiplatform Settings** (russhwolf) — wraps `SharedPreferences` / `NSUserDefaults`. Tiny, a few keys. Avoids dragging in Proto DataStore for one boolean.

---

### 4. Architecture

#### 4.1 New module layout

```
core/
  storage/                          # NEW
    model/                          #   SyncState enum, EntityRef, EntityCodec<T>
    datamanager/                    #   EntityStore<T> (local-only API), Koin
      impl/
        sqldelight/                 #   SqlDelightEntityStore (reads/writes SQLite)
        files/                      #   LocalBlobStore (filesystem + DB row)
  sync/                             # NEW
    engine/                         #   SyncEngine, push worker, pull listeners
    scheduler/                      #   UploadScheduler interface (expect/actual)
      androidMain/                  #     WorkManagerUploadScheduler
      iosMain/                      #     IosUploadScheduler (URLSession + BGProcessingTask)
    sharedassets/                   #   Strings for Settings row
```

Existing per-feature managers are refactored to depend on `EntityStore<T>` (local) instead of Firestore directly. **No manager talks to Firestore.** Firestore is touched only by `core/sync`.

#### 4.2 SQLDelight schema (generic, single)

```sql
CREATE TABLE entity (
  collection         TEXT    NOT NULL,    -- 'aircraft', 'maintenance_log', 'signature', …
  scope_path         TEXT    NOT NULL,    -- '/' or '/aircraft/N12345/'
  id                 TEXT    NOT NULL,
  payload            BLOB    NOT NULL,    -- proto-encoded bytes; never inspected by SQL
  payload_schema     TEXT    NOT NULL,    -- e.g. 'aircraft.Aircraft'
  updated_at         INTEGER NOT NULL,    -- local wall-clock, ms
  remote_updated_at  INTEGER,             -- Firestore server timestamp last seen, ms; null = never synced
  dirty              INTEGER NOT NULL DEFAULT 0,  -- 1 = local has writes not yet pushed
  deleted            INTEGER NOT NULL DEFAULT 0,  -- tombstone
  PRIMARY KEY (collection, scope_path, id)
);

CREATE INDEX entity_scope_idx  ON entity(collection, scope_path);
CREATE INDEX entity_dirty_idx  ON entity(dirty) WHERE dirty = 1;

CREATE TABLE blob_object (
  id             TEXT    NOT NULL PRIMARY KEY,  -- attachment id (also used in owning proto)
  scope_path     TEXT    NOT NULL,
  relative_path  TEXT    NOT NULL,              -- under filesDir/blobs/
  content_type   TEXT,
  size_bytes     INTEGER NOT NULL,
  sha256         TEXT    NOT NULL,
  remote_state   TEXT    NOT NULL,              -- 'LOCAL_ONLY'|'UPLOADING'|'SYNCED'|'REMOTE_ONLY'
  remote_path    TEXT,                          -- gs://… once uploaded
  updated_at     INTEGER NOT NULL,
  deleted        INTEGER NOT NULL DEFAULT 0
);
```

#### 4.2.1 Type discipline on `collection`, `payload_schema`, `remote_state`

Although the schema stores these fields as `TEXT`, **Kotlin code never sees raw strings.** Type safety lives at two chokepoints:

**Layer 1 — Kotlin sealed types + SQLDelight column adapters.** The `collection` column is mapped to a `sealed interface CollectionKind` (`Aircraft`, `MaintenanceLog`, `Technician`, `UserProfile`, `InspectionCard`, …) via a SQLDelight `ColumnAdapter`. Generated SQLDelight types expose `CollectionKind`, not `String`. Same treatment for `blob_object.remote_state` (sealed `RemoteState`). `payload_schema` stays `String` — it's intentionally open for forensics and one-shot encoding rewrites.

```kotlin
sealed interface CollectionKind {
  val wireName: String
  object Aircraft       : CollectionKind { override val wireName = "aircraft" }
  object MaintenanceLog : CollectionKind { override val wireName = "maintenance_log" }
  // … future: object Signature : CollectionKind { override val wireName = "signature" }
}
```

**Layer 2 — Codec registry as the only insertion path.** It is impossible to construct an `EntityStore<T>` for a `CollectionKind` that has no registered `EntityCodec<T>`. Koin wires `EntityStore<Aircraft>` to `(CollectionKind.Aircraft, AircraftCodec)`. Adding a domain is "register a codec" — never "scatter a string." Missing registration is a fail-fast at app start.

**Rejected: SQL-level `CHECK` constraint.** A `CHECK(collection IN (…))` would require a SQL migration every time a new domain is added (SQLite cannot drop a CHECK without rebuilding the table). That directly fights the "adding a new domain is zero schema change" property, so we skip it. The Kotlin sealed type already gives exhaustiveness at the only place it matters.

**Optional defense (deferred):** a `collection_kind(wire_name TEXT PRIMARY KEY)` lookup table with a foreign key from `entity.collection`. Populated by INSERT-OR-IGNORE during codec registration at app start. Buys SQL-level referential integrity with no per-domain migration cost. We'll add it if the codec registry ever proves insufficient as a chokepoint.

#### 4.2.2 Why this is future-proof

- Adding a new domain (e.g. signatures) is **zero schema change** — write rows with `collection = 'signature'` and a new codec.
- Renaming a proto field is invisible to the store (proto wire format is tag-based; bytes don't change).
- Removing a proto field is safe: unknown tags are ignored on decode.
- `payload_schema` lets us run a one-shot rewriter if we ever switch encoding.

#### 4.3 The `EntityStore` contract (local-only)

```kotlin
interface EntityStore<T : Any> {
  fun observeAll(scope: EntityScope): Flow<List<StorageEntity<T>>>
  fun observe(id: String, scope: EntityScope): Flow<StorageEntity<T>?>
  suspend fun put(id: String, value: T, scope: EntityScope)
  suspend fun delete(id: String, scope: EntityScope)
}

data class StorageEntity<T>(val id: String, val value: T, val updatedAt: Instant)
data class EntityScope(val segments: List<String>)
```

- `put` writes the row with `dirty=1`, `updated_at = now`.
- `delete` writes `deleted=1, dirty=1`.
- The sync engine consumes `dirty=1` rows; managers don't know it exists.

Each proto type registers an `EntityCodec<T>` (`T.ADAPTER.encode/decode` for Wire). The codec is what gives us proto-rename resilience: it operates on wire bytes.

#### 4.4 Reactive read path

```
SQLDelight change notification (collection, scope)
  → Flow<List<entityRow>>
  → map { codec.decode(it.payload) }
  → ViewModel.combine(...)
  → Compose UI
```

Identical with sync on or off. The sync engine's only effect is **inserting rows into the local DB**; the UI doesn't know who wrote them.

---

### 5. Proto Sync Engine

Identical on Android and iOS — runs in commonMain on top of the GitLive Firestore KMP bindings. **No platform-specific background execution.** Sync runs while the app is foregrounded; protos are tiny enough that draining the dirty queue on app open takes <1s.

#### 5.1 Push (local → cloud)

```
manager.put(aircraft):
  UPSERT entity SET payload=…, updated_at=now, dirty=1
  → UI updates immediately from SQLDelight

push worker (single coroutine while signed-in + sync-enabled):
  observe SELECT count(*) FROM entity WHERE dirty=1
  on positive count: drain
    for each dirty row (oldest updated_at first):
      firestore.doc(scope, id).set(
        payload-as-bytes + 'updated_at'=ServerTimestamp + 'deleted'=row.deleted
      )
      on success:
        UPDATE entity SET dirty=0, remote_updated_at=<server ts from ack>
      on transient error:
        exponential backoff, retry
      on permanent error (auth, rules):
        surface to user, leave dirty=1
```

#### 5.2 Pull (cloud → local)

```
on sync-enabled, for each registered (collection, scope):
  if first run for this (uid, collection):
    one-shot get() → bulk insert with dirty=0           # initial hydration
    record cursor
  attach firestore snapshot listener:
    for each remote doc change:
      remoteUpdatedAt = doc['updated_at']  (server ts)
      local = SELECT … WHERE id = doc.id
      if local == null:
        INSERT (payload=…, dirty=0, remote_updated_at=remoteUpdatedAt)
      else if local.dirty == 1 and local.updated_at > remoteUpdatedAt:
        skip          # our pending push will win on next ack
      else if remoteUpdatedAt > (local.remote_updated_at ?: 0):
        UPDATE payload=…, dirty=0, remote_updated_at=remoteUpdatedAt
      else:
        skip          # already up to date
```

The snapshot listener writes into the local DB, **not into the UI directly.** That indirection is what makes "disable sync" non-disruptive.

#### 5.3 Conflict resolution

Last-writer-wins keyed on Firestore **server timestamp**:

- Both devices write `updated_at = ServerTimestamp` when pushing.
- Firestore assigns the timestamp; whichever push lands second wins.
- The losing device sees the winner's doc on its next snapshot and overwrites local.
- Local `updated_at` (wall-clock) is used only for ordering pushes and for the user-visible "modified" time.

Race handled by the rule `if local.dirty=1 and local.updated_at > remoteUpdatedAt: skip` — we never clobber our own pending push.

#### 5.4 Tombstones

Deletes are not erasures.

- Local delete: `UPDATE entity SET deleted=1, dirty=1, updated_at=now`.
- Push: doc is written with `{deleted: true, updated_at: ServerTimestamp}` (we don't `delete()` the Firestore doc).
- Pull: on a doc with `deleted=true`, mark local row `deleted=1`.
- GC at app start: hard-delete tombstones older than 30 days.

#### 5.5 Initial hydration

First sign-in on a device:

1. Show a one-time progress indicator ("Syncing your fleet…").
2. For each registered collection, `firestore.collection(scope).get()` once.
3. Bulk insert with `dirty=0`, `remote_updated_at = doc.update_time`.
4. Attach snapshot listeners.

Realistically <2s for typical logbook size. After this, the user is in steady state.

#### 5.6 Why no background execution for protos

- Protos are tiny; full drain on app open is sub-second.
- The `dirty=1` flag in SQLite is the durable record. Process death mid-push is harmless.
- Users don't expect typed data to round-trip while the app is closed.
- WorkManager / BGProcessingTask machinery is overhead we don't need to pay here.

---

### 6. Attachment Sync

Different from protos in three ways that drive the design:

1. **Big** (1–10 MB). Can't share the proto sync loop.
2. **Immutable.** Edits are delete + re-add; conflict reduces to *exists / doesn't exist*.
3. **Two-step reference.** Owning proto holds `attachment_id`; blob lives separately. The two can sync at different rates.

#### 6.1 Five flows

**A. User adds an attachment.**
```
copy bytes → filesDir/blobs/{newId}
INSERT blob_object(remote_state='LOCAL_ONLY', sha256=…)
write owning proto referencing newId    (goes through normal proto sync)
enqueue UploadJob(blobId=newId)
```
File is openable immediately from the local path — no waiting on network.

**B. Upload worker (sync enabled).**
```
loop:
  pick blob_object where remote_state in ('LOCAL_ONLY','UPLOADING')
  set remote_state='UPLOADING'
  put bytes → gs://users/{uid}/blobs/{id}     (resumable)
  on success:  remote_state='SYNCED', remote_path=…
  on transient error:  revert to 'LOCAL_ONLY', retry with backoff
  on permanent error:  surface to user
```
Constraints: WiFi-only by default with a "Upload on cellular" toggle; single-flight per id; exponential backoff.

**C. Remote → local discovery.**
When proto sync pulls an `Aircraft` referencing an unknown `attachment_id = X`:
```
INSERT blob_object(id=X, remote_state='REMOTE_ONLY', remote_path=…, sha256=…)
```
Bytes are **not** downloaded yet. UI shows a placeholder ("Tap to download — 2.3 MB"). This keeps fresh-device sign-in from immediately pulling hundreds of MB.

**D. User opens an attachment.**
```
read blob_object by id
  if SYNCED or LOCAL_ONLY:  return file:// uri
  if REMOTE_ONLY:           download → verify sha256 → write file → SYNCED → return
  if UPLOADING:             return file:// uri  (local copy is fine)
```

**E. Deletion.**
```
mark blob_object.deleted=1, updated_at=now
remove attachment_id from owning proto (proto sync propagates)
DeleteJob: delete gs://… → hard-delete row + local file
```

#### 6.2 Conflict cases (and why they're easy)

| Scenario | Resolution |
|---|---|
| Same blob id from two devices | Impossible — ids are random per-device per-add. |
| Device A adds, B hasn't seen the proto yet | B sees it when proto arrives, lazy-downloads. |
| Device A deletes while B is offline | When B reconnects, proto sync removes the reference; B's blob_object becomes orphaned, GC'd. |
| Re-add of "the same file" semantically | New id, new blob. Optional sha256 dedupe later. |
| Sync toggled off mid-upload | Worker halts; blob stays `LOCAL_ONLY`. Re-enabling resumes. |

#### 6.3 Background execution — platform-specific

This is the only place where we cross the platform boundary.

```kotlin
// commonMain
interface UploadScheduler {
  fun scheduleUpload(blobId: String)
  fun scheduleDownload(blobId: String)
  fun cancelAll()        // called when user disables sync
}
```

**Android — `WorkManagerUploadScheduler`:**
- `CoroutineWorker` per blob, enqueued as **unique work** (`ExistingWorkPolicy.KEEP`) keyed by blob id.
- `Constraints` for WiFi-only (`UNMETERED`) by default; `CONNECTED` when cellular opt-in.
- `setBackoffCriteria(EXPONENTIAL, 30s)`.
- Survives process death and reboot. Standard playbook.

**iOS — `IosUploadScheduler` (two pieces):**
- **`URLSession` background configuration** — the OS performs the upload even if the app is suspended or terminated. Upload via signed URL to Firebase Storage REST endpoint; persist the resume session URL on `blob_object` so we can resume after a kill.
- **`BGProcessingTask`** (BackgroundTasks framework) — registered in `Info.plist` for periodic state-machine ticks ("scan for `LOCAL_ONLY`, schedule URLSession uploads"). The OS picks the moment (typically charging + WiFi).

**v1 simplification on iOS:** if integrating background `URLSession` with the GitLive Firebase Storage wrapper proves slow, ship iOS with **foreground-only uploads** initially (matches Android-foreground behavior; uploads pause when the user backgrounds the app and resume on return). Acceptable for a logbook where attachments are added in-context. Background `URLSession` lands in a follow-up.

#### 6.4 Security & integrity

- `sha256` stored at insert; verified on every download. Re-download on mismatch.
- Firebase Storage rules scope `gs://users/{uid}/blobs/*` to the owning user.
- iOS: `NSURLIsExcludedFromBackupKey` on `blobs/` so iCloud doesn't double-back-up files we already sync to Firebase.

#### 6.5 At a glance vs proto sync

| | Protos | Attachments |
|---|---|---|
| Size | bytes | MB |
| Trigger | every dirty row, immediately | scheduled job, WiFi-preferred |
| Foreground enough? | yes | no — needs OS background work |
| Conflict model | LWW on server timestamp | exists / doesn't exist |
| Platform-specific code | none — commonMain Firestore | Android: WorkManager / iOS: URLSession-bg + BGProcessingTask |
| Hydration on new device | bulk pull on sign-in | lazy, on first open |

---

### 7. Auth-state Behavior

#### 7.1 Scope is uniform across anonymous and permanent users

Firebase anonymous auth issues a real uid. Every user — anonymous or permanent — lives under `/users/{uid}/...`. There is no `/anonymous/` sentinel. The `CurrentUserScope` provider returns `firebaseAuth.currentUser?.uid` and feeds the same SQL queries for both cases.

The difference between anonymous and permanent is **policy on the sync engine**, not data layout: anonymous users' uid → sync engine refuses to start (toggle greyed). When the user upgrades to permanent, the policy gate flips and the engine can run.

#### 7.2 Transitions

| Transition | What happens |
|---|---|
| First launch (no cached Firebase session) | LoginScreen blocks the app. No `EntityStore` writes possible yet. Storage layer idle. |
| User picks "Continue as guest" on LoginScreen | `AuthManager.signInAnonymously()` runs → anonymous uid → app proceeds → first writes land under `/users/{anonUid}/...`. Sync toggle greyed. |
| User picks Google/email/Apple/etc. on LoginScreen | Permanent uid → app proceeds → if cursor for this uid exists, resume; otherwise initial hydration runs (§5.5). |
| Returning launch (Firebase session cached) | LoginScreen skipped; `AuthManager` exposes cached uid immediately. Storage and sync resume in steady state. |
| **Anonymous → permanent via `linkWithCredential`** (the recommended path) | **uid does not change.** Scope is unchanged. No row rewriting. The sync gate flips on (`isAnonymous` becomes false), sync defaults ON, push worker drains all pre-link rows (they are `dirty=1` because they were never synced). Initial hydration runs only if the linked account already has cloud data from some other device. |
| Anonymous → sign in to a *different* existing account (uid changes) | New uid means new scope. Sync engine boots for the new uid; initial hydration runs if needed. The anonymous-uid scope's rows remain on disk, hidden. Optional UX: offer "bring your offline work into this account," which copies/re-scopes anonymous rows into the new uid's scope and marks them `dirty=1`. Off by default — most users in this flow want their existing account, not the trial data. |
| Permanent → signed out | Sync engine cancels and saves cursor for that uid. Local DB retained (rows hidden behind scope filter). Firebase Auth signs out. App returns to LoginScreen; the next chosen path determines the next scope. |
| Permanent → re-signed in (returning) | Cursor for that uid exists → engine attaches listeners with `update_time > cursor`, pulls only the delta. `dirty=1` rows from the previous session drain. |
| Different permanent account signs in on same device | Their own uid, their own scope, their own initial hydration. Previous user's data remains in their scope, untouched. |
| Sync ON → OFF | Listeners detach, push worker stops. Local unchanged. UI unaffected. |
| Sync OFF → ON | Listeners attach, push worker drains accumulated dirty rows. |
| "Remove this account from this device" (explicit wipe) | Hard-delete all rows + blobs under `/users/{uid}/...`, clear cursor and preference for that uid. Cloud copy untouched. Re-signing in later runs full hydration. |

The "scope per uid" rule is what allows the same physical device to host multiple users' data without leakage and without forced wipes. Because anonymous users share the same scope shape, the **`linkWithCredential` upgrade path is zero-work** — the cleanest possible onboarding.

---

### 8. Digital Signatures (forward-looking)

When signatures land:

1. Add `signature.proto` (e.g. `repeated Stroke strokes`, `Stroke { repeated Point points }`).
2. Add `EntityCodec<Signature>`; inject `EntityStore<Signature>` via Koin.
3. Reference signatures from existing protos by id (`string signature_id = …`).

Cost: one proto + one codec line. **No SQL migration. No sync-engine change.** The signature flows through the same push/pull path as every other proto.

---

### 9. Existing-User Migration

Today's users have data in Firestore and nothing local. On first launch of the new build:

1. The local DB is empty; sync is ON by default for signed-in users.
2. **Initial hydration** runs (§5.5) — pulls every collection from Firestore into local.
3. From this point on, the app reads from local.

This is the same code path as "fresh sign-in on a new device." No special migration job. Users see a one-time progress indicator and then their app is local-first.

---

### 10. Risks & Open Questions

| Risk | Mitigation |
|---|---|
| Sync engine bugs cause data divergence between devices | Contract test suite that scripts pairs of devices through hydration / push / pull / tombstone scenarios; LWW invariants asserted post-condition. |
| Initial hydration is slow for power users with hundreds of logs | Stream into local in chunks; show count progress; UI usable for already-loaded collections. |
| `dirty=1` rows that never push (auth lapsed, rule failure) accumulate | Surface a persistent "X items not synced — tap to retry" banner when sync is on but push is failing. |
| Firestore cost grows with snapshot listener fanout | We attach one listener per (collection, scope). Listeners are cheap; reads are not. Bound the listener set to the user's own scope. |
| iOS background uploads with GitLive Firebase Storage | If the wrapper doesn't expose a background `URLSession`, drop to platform code using signed-URL REST upload. v1 can ship foreground-only on iOS. |
| Proto field marked required then later removed | Wire forbids this. Lint rule + code review to keep all fields optional. |
| User toggles sync on/off rapidly | Sync engine init/teardown is idempotent; a debounce on the toggle (200ms) prevents thrash. |
| Local DB corruption (rare but real) | On open, run `PRAGMA integrity_check`; on failure, prompt user to "re-sync from cloud" (delete local, re-hydrate). Anonymous users get a recoverable error and a backup-export hook. |
| Storage size on device | Logbook data is KB-MB total; attachments are the only real footprint, and `REMOTE_ONLY` placeholders mean they're lazy-downloaded. |

---

### 11. Milestones

1. **M1 — Foundations.** New `core/storage` module: SQLDelight schema, `CollectionKind` sealed type + `ColumnAdapter`, `EntityStore<T>` interface, `EntityCodec<T>` registry, contract tests. `EntityStore` requires a non-null `currentUid` from `AuthManager`; storage is dormant until LoginScreen completes. No sync, no UI changes.
2. **M2 — Manager refactor.** Migrate `FleetManager`, `MaintenanceLogManager`, `TechnicianManager`, `UserProfileManager`, `TaskDataManager`, `TaskDueManager` to `EntityStore`. Firestore code removed from managers.
3. **M3 — Proto sync engine.** `core/sync` module: push worker, snapshot listeners, LWW, tombstones, initial hydration, per-uid cursor. Sync gate keyed on `isAnonymous == false`. Defaults ON for permanent users.
4. **M4 — Attachments local backend.** `LocalBlobStore`, `AttachmentManager` rewrite, `AttachmentOpener` updates for `file://` URIs.
5. **M5 — Attachment sync.** `UploadScheduler` interface; Android WorkManager impl; iOS foreground-first impl. Lazy `REMOTE_ONLY` downloads with the attachments index.
6. **M6 — Settings UI + auth wiring.** "Enable Sync" toggle (greyed for anonymous), `linkWithCredential` upgrade flow, "Remove this account from this device" action, persistent "not synced" banner on push failure.
7. **M7 — Polish.** iOS background `URLSession` upload, contract test report, integrity-check recovery flow, docs.

#### 11.1 Release strategy

Milestones bundle into two user-facing releases. Each release is internally consistent: data flowing through the new path on the inside also reaches the cloud on the outside, so users never see "the app stopped backing up" mid-flight.

| Release | Bundles | What users perceive | What's still on the old path |
|---|---|---|---|
| **R1 — Local-first protos** | M1 + M2 + M3 (+ M6 minimum: "Enable Sync" toggle, `linkWithCredential`) | App is offline-capable for all logbook data. Reads come from local SQLite, latency drops to zero. Anonymous "guest" users can use every screen except attachments. | Attachment **binaries** still upload directly to Firebase Storage; the URL is stored in the owning proto as today. The proto itself rides the new sync engine. |
| **R2 — Local-first attachments** | M4 + M5 (+ M7 polish) | Photos can be added offline. Lazy `REMOTE_ONLY` placeholders appear on new devices; tapping downloads. Anonymous users can now attach files. | Nothing — feature complete. |

**Why M2 and M3 must ship together.** End of M2 alone means managers write only to local SQLite — no Firestore writes, no cloud backup. Shipping that without M3 would silently break sync for every user. Treat M2/M3 as a single atomic release unit; gate behind a feature flag during the M2-only window so internal builds can exercise the local read path without exposing it to users.

**Why M4 and M5 must ship together** (same reason). End of M4 makes the AttachmentManager write bytes to `filesDir/blobs/` instead of uploading; without M5's upload worker, those bytes never reach the cloud and the attachment is local-only on that device. Bundle M4+M5; flag-gate during the M4-only window.

**M6 spans both releases.** The "Enable Sync" toggle and `linkWithCredential` upgrade flow are needed in R1. The "Remove this account from this device" action can land in R1 or R2 — non-blocking either way. The persistent "not synced" banner is needed in whichever release first introduces a sync engine that can fail (R1).

**Rollout plan per release:**

1. Internal dogfood for 1–2 weeks behind a build-time flag.
2. Staged rollout (10% → 50% → 100%) on the relevant store; same flag, server-controlled.
3. Monitor Firestore read/write counts and attachment storage egress for regressions; expect reads to drop sharply (no more snapshot-driven UI), writes to stay flat, attachment egress to drop in R2 as lazy downloads avoid eager fetches.

Out of scope this PRD: end-to-end encryption, multi-user collaboration on a single account, CRDT-based conflict resolution, signatures (door is open, build is later).
