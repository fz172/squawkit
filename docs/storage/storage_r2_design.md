# R2 Design: Local-First Attachments

**Branch:** `r2-attachments-design`
**PRD:** [`storage_mode_PRD.md`](storage_mode_PRD.md) — §6, §11.1 R2
**Companion:** [`storage_r1_design.md`](storage_r1_design.md) — R1 sets the proto-sync foundation this doc builds on.
**Scope:** M4 (local blob store + AttachmentManager rewrite + opener) + M5 (upload scheduler + lazy download) + the R2 portion of M7 (iOS background `URLSession`, integrity-check recovery).

---

## Implementation status — ✅ SUBSTANTIALLY IMPLEMENTED (UI behind a feature flag)

The R2 architecture is built and wired:

- `feature/attachment/` module exists with `model/`, `datamanager/`, `sharedassets/`, and `viewing/` submodules.
- `core/storage` ships the `blob_object` table plus the `RemoteState` / `BlobId` types.
- The blob sync drivers live in `feature/sync/data/blob/` — `BlobUploadDriver`, `BlobDownloadDriver`,
  `BlobDeleteDriver` — with platform schedulers: `WorkManagerUploadScheduler` (Android) and
  `UrlSessionUploadScheduler` + `BGProcessingTask` (iOS), plus a foreground scheduler.
- Attachments are consumed by logs, tasks, and squawks.
- **Storage path change:** blobs upload to `users/{uid}/aircraft/{aircraftId}/blobs/{id}` (aircraft-scoped),
  not the flat `users/{uid}/blobs/{id}` this doc originally specified — see the history note in §3.

**Gating:** the attachment UI (and the *Sync on Cellular* toggle) is shown only when the `attachmentUploadEnabled`
feature-lab flag is on — so the feature is present but not yet enabled for general use.

**De-registration:** `core/attachments` was removed from `settings.gradle.kts` (superseded by `feature/attachment`),
though its stale build files still exist on disk and are slated for deletion.

---

## 1. Goals & non-goals

**In:**
- All attachments — files, images, PDFs — are written to the device first (`filesDir/blobs/{id}`) and uploaded to Firebase Storage in the background.
- Attachments added offline are usable on that device immediately and upload when network/auth allow.
- Anonymous users can attach files. The R1 anonymous-blocked path is removed.
- New devices for an existing account see attachment placeholders (`REMOTE_ONLY`) and download bytes lazily on first open.
- A binary integrity check (`sha256`) on every download.
- Per-parent size cap (25 MB), per-user storage cap (1 GB), and per-parent duplicate-file rejection — all enforced in the picker before a file is accepted.

**Out:**
- Per-device attachment encryption (existing Firebase Storage rules suffice).
- CRDT-style merging — attachments are immutable; conflict reduces to *exists / doesn't exist*.
- Block-level dedupe across attachments (sha256 dedupe is a future optimisation).
- Video files (deferred per attachments PRD §Non-Goals).
- **Backward compatibility with R1-era attachments.** R2 is a clean break: existing attachments written by R1 builds are not migrated and will not open in R2. This is a one-time, deliberate break — see §10. The cost is bounded (only existing users with attachments are affected; the user base is small) and the simplification across the proto, opener, and migration code is significant.

---

## 2. Module layout

R2 introduces a new `feature/attachment` module (canonical layout) and **deletes `core/attachments`** — its content is rewritten anyway (clean break, §10) so we relocate into the canonical pattern instead of perpetuating the pre-canonical `core/` placement. Generic blob persistence (table + sealed type + adapter) stays in `core/storage`, mirroring how `sync_cursor` is a table in `core/storage/Schema.sq` but its API (`SyncCursorStore`) lives in `feature/sync/data`.

```
core/
  storage/                                 # M1 — schema only is extended in R2
    src/commonMain/sqldelight/.../db/
      Schema.sq                            #   adds blob_object table + queries (§4)
    src/commonMain/kotlin/.../core/storage/blob/
      RemoteState.kt                       #   sealed interface + ColumnAdapter
      BlobId.kt                            #   value class

  attachments/                             # REMOVED — relocated into feature/attachment/*

feature/
  attachment/                              # NEW — replaces core/attachments
    model/                                 #   PendingAttachment, BlobRef, AttachmentStatus,
                                           #   QuotaResult, DownloadState, OpenState, UploadState
    datamanager/
      src/commonMain/kotlin/.../feature/attachment/datamanager/
        LocalBlobStore.kt                  #   interface (§6.1)
        BlobFilesystem.kt                  #   expect — filesDir, read/write/delete
        AttachmentManager.kt               #   interface (§6.2)
        AttachmentOpener.kt                #   expect (§7)
        QuotaChecker.kt                    #   §9b
        impl/
          SqlDelightLocalBlobStore.kt      #   wraps WingsLogDatabase blob_object queries
          LocalFirstAttachmentManagerImpl.kt
        di/AttachmentModule.kt             #   Koin
      src/androidMain/.../impl/
        BlobFilesystem.android.kt          #   Context.filesDir
        AttachmentOpenerAndroid.kt
      src/iosMain/.../impl/
        BlobFilesystem.ios.kt              #   NSDocumentDirectory + NSURLIsExcludedFromBackupKey
        AttachmentOpenerIos.kt
    sharedassets/                          #   strings (incl. quota/dedupe/legacy error copy), icons
    viewing/                               #   AttachmentRow, AttachmentSection, status badge
    update/                                #   picker bottom sheet, link entry, viewmodel glue

  sync/
    data/                                  # M3 — extended in R2
      src/commonMain/kotlin/.../sync/data/blob/
        BlobUploadDriver.kt                #   one-shot upload of a blob_object row
        BlobDownloadDriver.kt              #   one-shot download + sha256 verify
        BlobIndexReconciler.kt             #   inserts REMOTE_ONLY rows when protos pull
        UploadScheduler.kt                 #   expect interface
      src/androidMain/kotlin/.../sync/data/blob/
        WorkManagerUploadScheduler.kt      #   actual: WorkManager
      src/iosMain/kotlin/.../sync/data/blob/
        ForegroundUploadScheduler.kt       #   actual: foreground-only (R2 ship-able)
        UrlSessionUploadScheduler.kt       #   actual: URLSession bg + BGProcessingTask (M7)
```

**Why blob_object stays in `core/storage`.** It's a SQLDelight table inside the same `WingsLogDatabase` as `entity` and `sync_cursor`. SQLDelight generates one set of typed queries per database; splitting tables across modules would mean splitting databases and losing cross-table transactions. The `RemoteState` sealed type + adapter live in `core/storage` for the same reason `CollectionKind` does — SQLDelight-generated code references them directly.

**Why upload/download drivers stay in `feature/sync/data`.** They're sync-engine code (one driver per blob, scheduled by the sync layer); they aren't attachment-specific UI/business logic. Symmetric with how `PullListener` lives there in R1.

`composeApp/di/initKoin.kt`: swap the Koin binding for `AttachmentManager` from R1's Firebase-direct impl (in the now-deleted `core/attachments`) to the new local-first impl in `feature/attachment/datamanager`, and add `blobUploadModule` to the aggregate.

---

## 3. Goals of the data model split

R1 keeps the entire `Attachment` proto (id, name, mime_type, size_bytes, storage_path, download_url) inside the owning entity's proto bytes. R2 adds a parallel `blob_object` row for **every** attachment whose `type` is not `LINK`. The proto remains the source of truth for *metadata*; `blob_object` is the source of truth for *byte location and sync state*.

```
owning proto (e.g. MaintenanceLog)
  ├─ Attachment id="att-7", name="SB-23-15.pdf", mime, size, sha256
  │     (rides the M3 entity sync — already works in R1)
  │
  └─ ────[ id is the join key ]────
                                    │
blob_object table (R2)              │
  id="att-7", relative_path="blobs/att-7.bin",
  sha256, size_bytes, content_type,
  remote_state=LOCAL_ONLY|UPLOADING|SYNCED|REMOTE_ONLY,
  remote_path="users/{uid}/aircraft/{acId}/blobs/att-7", deleted=0
```

`blob_object` is **device-local only** — it is never synced. The proto carries everything another device needs (id, sha256, size, mime, name) to recreate a `REMOTE_ONLY` row when it sees the proto.

This split is the core lemma of M4+M5: the attachments PRD's existing flows already populate the proto; we add a sibling table that mirrors only what the device needs to manage bytes.

### Why not extend the `entity` table?

`entity` rows participate in proto sync (push / pull / LWW). Blobs do not — they don't have a Firestore document of their own; the binary lives in Firebase Storage and the metadata rides inside the owning entity's proto. Putting blob state into `entity` would require either (a) a `payload_schema = 'blob_object_state'` row that we never sync (a special case), or (b) adding sync-suppression columns. A second table with no sync columns is simpler.

### Proto changes (`attachment.proto`)

Add `sha256` so a remote device can construct a placeholder row and verify bytes on download. Drop `download_url` — R2 doesn't pre-fetch download URLs, and there is no R1 fall-through to support.

```proto
message Attachment {
  string id            = 1;
  string name          = 2;
  AttachmentType type  = 3;
  string storage_path  = 4;  // 'users/{uid}/aircraft/{aircraftId}/blobs/{id}' for non-LINK; empty for LINK
  reserved             5;    // was download_url; removed in R2 (clean break)
  reserved "download_url";
  string url           = 6;  // populated for LINK only
  string mime_type     = 7;
  int64  size_bytes    = 8;
  google.protobuf.Timestamp created_at = 9;
  string sha256        = 10; // NEW — hex; required for non-LINK
}
```

Field 5 is reserved (Wire/protoc enforces no future reuse). R1 builds that try to decode an R2-written `Attachment` will see `download_url = ""` and a populated `sha256`; they have no path to open such a file (R1 opener requires `download_url`). This is the intentional break called out in §1.

`storage_path` is `users/{uid}/aircraft/{aircraftId}/blobs/{id}` — derived from the owning entity's `EntityScope` with the shared formula `${scope.toPath().trim('/')}/blobs/{id}` (the same formula used by `SqlDelightLocalBlobStore.upsertRemoteOnly` and `BlobUploadDriver`).

> **History — flat path superseded (PR #40).** This design originally normalised `storage_path` to a flat
> `users/{uid}/blobs/{id}` (no per-aircraft subpath), and R2 PR 3 (#37) shipped that way. PR 5c/5d (#40)
> re-scoped blobs to their aircraft to match the hierarchical entity model. Blobs uploaded by the interim
> builds still sit at the flat path in Firebase Storage; they were **deliberately not migrated**. On a fresh
> device the `BlobIndexReconciler` recomputes `remote_path` from the owning entity's scope (it ignores the
> proto's `storage_path`), so flat-era blobs 404 on download anywhere but the uploading device. This is an
> accepted break, same spirit as the R1 clean break in §10; the orphaned flat-path objects can be cleaned up
> manually via the console.

### Why `sha256` in the proto and not just the `blob_object` row?

A new device that only has the proto needs to know the expected sha256 *before* it has downloaded the bytes — otherwise on download we cannot verify integrity, and the placeholder row cannot be populated with anything other than `null`. Putting the hash in the proto is one extra ~64-byte string per attachment; trivial and correct.

---

## 4. SQLDelight schema (`Schema.sq` additions)

```sql
import dev.fanfly.wingslog.core.storage.blob.RemoteState;

CREATE TABLE blob_object (
  id             TEXT    NOT NULL PRIMARY KEY,    -- attachment id; same as Attachment.id in proto
  scope_path     TEXT    NOT NULL,                -- owning entity's scope, '/users/{uid}/aircraft/{acId}/'
  relative_path  TEXT    NOT NULL,                -- 'blobs/{id}.bin' under filesDir
  content_type   TEXT,                            -- e.g. 'image/jpeg'; null acceptable
  size_bytes     INTEGER NOT NULL,
  sha256         TEXT    NOT NULL,                -- hex
  remote_state   TEXT    AS RemoteState NOT NULL,
  remote_path    TEXT,                            -- '{scope_path}/blobs/{id}' once SYNCED, null otherwise
  upload_attempts  INTEGER NOT NULL DEFAULT 0,    -- consecutive transient failures
  last_attempt_at  INTEGER,                       -- epoch ms
  updated_at     INTEGER NOT NULL,                -- last local mutation (for GC ordering)
  deleted        INTEGER AS kotlin.Boolean NOT NULL DEFAULT 0
);

CREATE INDEX blob_state_idx ON blob_object(scope_path, remote_state);
CREATE INDEX blob_pending_idx ON blob_object(remote_state, last_attempt_at);
```

Selected queries:

```sql
upsertBlob:
INSERT OR REPLACE INTO blob_object(
  id, scope_path, relative_path, content_type, size_bytes, sha256,
  remote_state, remote_path, upload_attempts, last_attempt_at, updated_at, deleted
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

selectBlobById:
SELECT * FROM blob_object WHERE id = ?;

selectPendingUploads:
SELECT * FROM blob_object
WHERE deleted = 0
  AND remote_state IN ('LOCAL_ONLY', 'UPLOADING')
  AND scope_path LIKE :scopePrefix
ORDER BY updated_at ASC
LIMIT :limit;

selectOrphanedTombstones:
SELECT * FROM blob_object
WHERE deleted = 1
ORDER BY updated_at ASC
LIMIT :limit;

setRemoteState:
UPDATE blob_object SET remote_state = ?, remote_path = ?, last_attempt_at = ?, upload_attempts = ?
WHERE id = ?;

markBlobDeleted:
UPDATE blob_object SET deleted = 1, updated_at = ? WHERE id = ?;

hardDeleteBlob:
DELETE FROM blob_object WHERE id = ?;
```

`RemoteState` is a sealed type (parallel to `CollectionKind`) with a SQLDelight `ColumnAdapter`. `LOCAL_ONLY → UPLOADING → SYNCED` is the upload happy path; `REMOTE_ONLY → SYNCED` is the download happy path (in this direction `LOCAL_ONLY` is never re-entered after `SYNCED`).

### Why `scope_path` on blobs

Required for two reasons:
1. **Push gating.** The push worker only drains rows where `scope_path LIKE '/users/{currentUid}/%'`. Without scope, an anonymous user's blobs would be eligible to push under a permanent user's auth.
2. **`linkWithCredential` upgrade.** When an anonymous account is linked, the uid is unchanged (R1 §7.3) — blobs with `scope_path = '/users/{anonUid}/'` automatically become eligible because `anonUid` is now permanent and authorised.

### Why no remote-side index

The PRD §6.1.C says "INSERT blob_object on proto pull when an unknown id appears." That insert is driven by **`BlobIndexReconciler`** running when the entity sync layer commits a pulled proto (§8.3). It walks the proto's `repeated Attachment` field and `INSERT OR IGNORE`s a `REMOTE_ONLY` row per non-LINK attachment id that doesn't already exist. No separate Firestore "attachments index" collection; the proto *is* the index.

---

## 5. State machine

```
                            ┌───────────────┐
       AttachmentManager.add│  LOCAL_ONLY   │
       ──────────────────► │ bytes on disk │
                            │ no remote_path│
                            └───────┬───────┘
                                    │
            UploadScheduler picks   │
            up; sets state          │
                                    ▼
                            ┌───────────────┐
                            │   UPLOADING   │ ◄──── transient error: revert
                            │ in-flight to  │       to LOCAL_ONLY, attempts++
                            │ Firebase Stg  │       (capped — see §5.1)
                            └───────┬───────┘
                                    │
                       upload OK    │
                                    ▼
                            ┌───────────────┐
                            │    SYNCED     │
                            │ remote_path   │
                            │ populated     │
                            └───────────────┘
                                    ▲
            new device sees proto   │
            with attachment id X    │
                                    │
                            ┌───────────────┐
            opens it,       │  REMOTE_ONLY  │
            verifies sha256 │ no local file │
            ──────────────► │ remote_path   │
                            │ populated     │
                            └───────────────┘
```

### 5.1 Failure budgets

- **Transient upload error** (network, 5xx): `state = LOCAL_ONLY`, `upload_attempts++`, scheduled with exponential backoff `(2^attempts × 30s)` capped at 60min. The scheduler does the backoff math; the row itself just records the counter.
- **Permanent upload error** (auth lost, 403, file no longer exists on disk): `state = LOCAL_ONLY`, surface a per-attachment error to the UI via `AttachmentManager.observeUploadStatus(id)` — the user can manually retry from the attachment row UI (M6 surface). The row is **not** marked `deleted`; a retry from a re-authenticated state will pick it up.
- **Download verification failure** (sha256 mismatch): delete the local file, leave `state = REMOTE_ONLY`, surface `OpenState.Failed(IntegrityError)`. User retries.

`upload_attempts` is reset to 0 on the first successful upload (so a flaky network earlier in the day doesn't cap the next blob's retries). It is **not** reset by a sync-toggle off → on; the user toggling sync isn't a signal that the bytes have changed.

### 5.2 Why no separate `DELETING` state

Deletes are a one-way trip: `mark deleted=1` → enqueue `DeleteJob` → `DeleteJob` removes the gs:// object → hard-delete row + local file. If the row is still in `LOCAL_ONLY` the gs delete is a no-op. If the device dies mid-delete, the next `BlobUploadDriver` tick re-encounters `deleted=1` and resumes. No interim state needed.

---

## 6. `LocalBlobStore` and `AttachmentManager` rewrite

### 6.1 `LocalBlobStore` (in `core/storage`)

```kotlin
interface LocalBlobStore {
  /** Write bytes to filesDir/blobs/{id}, insert/update blob_object row LOCAL_ONLY. */
  suspend fun put(id: BlobId, bytes: ByteArray, contentType: String?, scope: EntityScope): BlobRef

  /** Insert a placeholder row from proto metadata (no local file yet). */
  suspend fun upsertRemoteOnly(
    id: BlobId, sha256: String, sizeBytes: Long, contentType: String?, scope: EntityScope,
  )

  fun observe(id: BlobId): Flow<BlobRef?>
  suspend fun get(id: BlobId): BlobRef?

  /** Returns a file:// URI if local bytes exist, else null. */
  suspend fun localUri(id: BlobId): String?

  suspend fun markUploaded(id: BlobId, remotePath: String)
  suspend fun markFailedTransient(id: BlobId)
  suspend fun markFailedPermanent(id: BlobId, cause: Throwable)

  /** Verify, then write bytes from a download into filesDir; flips REMOTE_ONLY → SYNCED. */
  suspend fun installDownloaded(id: BlobId, bytes: ByteArray, expectedSha256: String): Result<Unit>

  /** Tombstones the row + deletes local file. Remote object cleanup is the caller's job. */
  suspend fun delete(id: BlobId)
}

data class BlobRef(
  val id: BlobId,
  val sizeBytes: Long,
  val sha256: String,
  val contentType: String?,
  val remoteState: RemoteState,
  val remotePath: String?,
  val deleted: Boolean,
)
```

`LocalBlobStore` is the only path that writes `blob_object`. Tests (see §11) treat it as a contract.

### 6.2 `AttachmentManager` (rewritten)

The R1 surface depended on Firebase paths and download URLs because the manager was the upload boundary. After R2, the manager is a thin coordinator:

```kotlin
interface AttachmentManager {
  /**
   * Add a picked file: copies bytes into the local blob store and returns a populated
   * Attachment proto (with sha256, mime, size, etc.) ready to be embedded in the owning entity.
   * Anonymous users no longer blocked — bytes are local and will sync after sign-in.
   */
  suspend fun addPickedFile(picked: PickedFile, displayName: String): Attachment

  /**
   * For LINK attachments — no blob row, just a populated proto.
   */
  fun makeLink(url: String, displayName: String): Attachment

  /**
   * Mark the attachment for deletion. Removes the proto reference (caller's responsibility)
   * and tombstones the blob_object so the upload/delete worker cleans up gs://.
   */
  suspend fun delete(attachment: Attachment)

  /**
   * For an attachment whose blob is REMOTE_ONLY: trigger a foreground download.
   * Verifies sha256 before writing the file. No-op if SYNCED locally.
   */
  fun ensureLocal(attachment: Attachment): Flow<DownloadState>

  /** Read-only view of upload pipeline status for UI (per-attachment). */
  fun observeStatus(attachmentId: String): Flow<AttachmentStatus>
}

data class PickedFile(val uri: String, val name: String, val mimeType: String, val sizeBytes: Long)

sealed class DownloadState {
  data class Downloading(val progress: Float) : DownloadState()
  data object Done : DownloadState()
  data class Failed(val error: Throwable) : DownloadState()
}

sealed class AttachmentStatus {
  data object LocalOnly : AttachmentStatus()
  data class Uploading(val progress: Float) : AttachmentStatus()
  data object Synced : AttachmentStatus()
  data object RemoteOnly : AttachmentStatus()
  data class Failed(val error: Throwable) : AttachmentStatus()
}
```

The R1 `uploadFile(...) → Flow<UploadState>` is gone — `addPickedFile` returns synchronously after writing local bytes; uploads happen out-of-band via the scheduler. Form ViewModels no longer wait on uploads to save (a long-standing PRD goal — N1 and N2 in `attachments_PRD.md` are now satisfied without coupling the save flow to the network).

### 6.3 Save flow becomes synchronous

Old (R1):
```
User taps Save
  → upload all PendingAttachment.Local in parallel  ← waits on network
  → on success, write parent entity with download_urls
```

New (R2):
```
User taps Save
  → for each pending Local: attachmentManager.addPickedFile(...) → Attachment proto
       (writes bytes locally + creates LOCAL_ONLY row; ms-scale)
  → write parent entity with the populated attachments list  (proto sync handles the rest)
  → UploadScheduler kicks in asynchronously; upload status surfaces through observeStatus
```

The R1 save-level progress spinner is replaced by a per-attachment status icon in the viewing row (see §6.4).

### 6.4 Sync-state display (`BlobSyncState`)

#### Domain type

`feature/attachment/model/` exposes a sealed type the viewing layer uses directly, hiding `RemoteState` + upload-counter details from composables:

```kotlin
sealed interface BlobSyncState {
  data object PendingUpload : BlobSyncState  // LOCAL_ONLY, upload_attempts == 0
  data object Uploading     : BlobSyncState  // UPLOADING (in-flight)
  data object Synced        : BlobSyncState  // SYNCED
  data object RemoteOnly    : BlobSyncState  // REMOTE_ONLY (no local file yet)
  data object Downloading   : BlobSyncState  // download in-flight (AttachmentOpener)
  data object UploadFailed  : BlobSyncState  // LOCAL_ONLY, upload_attempts > 0
}
```

`BlobSyncState` is derived from `blob_object.remote_state` + `upload_attempts > 0`. No new `RemoteState` enum value is needed.

#### Data pairing

```kotlin
data class AttachmentWithState(
  val attachment: Attachment,
  val syncState: BlobSyncState?,  // null = link type (no blob lifecycle)
)
```

#### Data flow

`LocalFirstAttachmentManagerImpl` (or a thin `AttachmentStateProvider`) exposes:

```kotlin
fun observeBlobStates(scopePath: String): Flow<Map<String, BlobSyncState>>
```

The ViewModel combines this with `attachmentOpener.downloadingIds`, applying `Downloading` for any id present in that set, and passes `List<AttachmentWithState>` to the composable layer.

#### Composable changes

`AttachmentSection` and `AttachmentRow` change their primary list parameter from `List<Attachment>` + `Set<String> downloadingIds` to `List<AttachmentWithState>`. The `downloadingIds` set is folded in by the ViewModel before the call; composables no longer need it as a separate argument.

The trailing icon slot in `AttachmentRow` renders the status icon per the table in `../attachments/attachments_PRD.md` §Viewing. The `OpenInNew` arrow is shown alongside the status icon only when the row is tappable (`Synced`, `RemoteOnly`, link). For `PendingUpload` and `UploadFailed` the `OpenInNew` arrow is hidden.

Tapping an `UploadFailed` row triggers a prompt; the handler calls `AttachmentManager.retryUpload(id)` which resets `upload_attempts = 0` so the uploader picks the row up on its next pass.

---

## 7. `AttachmentOpener` changes

R2 routes opens through `LocalBlobStore`:

```
opener.open(attachment) →
  if attachment.type == LINK: open attachment.url in system browser
  else:
    blob = blobStore.get(attachment.id)
    when blob?.remoteState:
      LOCAL_ONLY, SYNCED, UPLOADING → open file:// at filesDir/blobs/{id}
      REMOTE_ONLY                   → emit Downloading; download via Firebase Storage SDK;
                                       blobStore.installDownloaded(); on success emit Done; open file://
      null (no row)                 → emit Failed(MissingBlobIndex); the user should not see this in
                                       practice — BlobIndexReconciler runs on every proto pull and
                                       inserts the placeholder row before the UI renders the attachment.
                                       If sha256 is empty (R1-era attachment), surface
                                       Failed(LegacyAttachment) — see §10.
```

There is no fall-through to a stored download URL. Every R2 fetch goes through `FirebaseStorage.getDownloadUrl(storage_path)` at open time (or whatever signed-URL mechanism the SDK uses) — URL freshness is no longer something the proto carries.

---

## 8. Upload scheduler & workers

### 8.1 `UploadScheduler` (expect)

```kotlin
expect class UploadScheduler {
  fun scheduleUpload(blobId: BlobId)
  fun scheduleDownload(blobId: BlobId)
  fun scheduleDelete(blobId: BlobId)
  fun cancelAll()                   // user toggles sync off
  fun cancelForUid(uid: String)     // "remove account from this device"
  val networkConstraint: StateFlow<NetworkConstraint>  // user pref: WIFI_ONLY | ANY
}
```

The scheduler does not perform I/O itself. It enqueues work that wakes a `BlobUploadDriver` / `BlobDownloadDriver` / `BlobDeleteDriver`, all of which live in `feature/sync/data/blob/` and are commonMain. The scheduler is platform-specific only because *what wakes the worker* is platform-specific.

### 8.2 Drivers (commonMain)

```kotlin
class BlobUploadDriver(
  private val blobs: LocalBlobStore,
  private val storage: FirebaseStorage,
  private val auth: AuthManager,
  private val fs: BlobFilesystem,
) {
  /** Returns true on terminal success (or successful no-op); false on retryable failure. */
  suspend fun runOnce(id: BlobId): Boolean
}
```

`runOnce` is idempotent and the unit each platform's worker calls per scheduled job. WorkManager retries failures; the driver returns a typed result so the worker decides retry vs surface.

### 8.3 `BlobIndexReconciler`

Hooked into `PullListener`'s post-write step. After a proto with `repeated Attachment` lands, the reconciler:

1. For each attachment with `type != LINK`, `sha256` non-blank, and `id` not present in `blob_object`:
   - `blobStore.upsertRemoteOnly(id, sha256, size_bytes, mime_type, scope)`
   Attachments with blank `sha256` are R1-era — skipped, so no row is created and the opener surfaces `Failed(LegacyAttachment)` (§10).
2. For each blob_object row whose proto reference no longer exists across **any** entity in the user's scope:
   - Mark `deleted=1` (orphan GC). Done in batches at app start, not per-pull (cheap).

The orphan check is "no entity row references this blob id." With R2's `attachments` field on each entity, this is a cross-row aggregate; we run it on a 24h cadence and on app cold start. Until then, orphans remain on disk — wasted space, not data loss.

### 8.4 Per-platform schedulers

**Android — `WorkManagerUploadScheduler`:**
- One `CoroutineWorker` class (`BlobUploadWorker`) with a `blobId` input.
- Enqueued as **unique work** keyed by `"upload:${blobId}"` with `ExistingWorkPolicy.KEEP`.
- `Constraints.Builder().setRequiredNetworkType(UNMETERED or CONNECTED based on user pref)`.
- `setBackoffCriteria(EXPONENTIAL, 30s)`. Retry budget is WorkManager's default (10).
- Survives process death and reboot.

**iOS — phased ship:**
- **R2 v1**: `ForegroundUploadScheduler` — runs uploads while app is foregrounded; matches the R1 attachment behaviour on iOS (where uploads paused on backgrounding anyway). Acceptable initial parity.
- **R2 v2 (M7)**: `UrlSessionUploadScheduler` — `URLSession` background configuration (signed-URL REST upload to Firebase Storage), `BGProcessingTask` registered in `Info.plist` for OS-driven scan-and-schedule ticks. The driver is the same; only the wakeup mechanism changes.

The split lets R2 ship with full Android background support and iOS-foreground; neither blocks the other. The **"Allow upload on cellular"** toggle (Settings → Backup & Sync, defaults OFF — see §13.1) is a single `Boolean` pref read live by both schedulers via the `networkConstraint` `StateFlow`. Flipping it ON re-evaluates pending uploads on the next driver tick.

---

## 9. Auth integration

Two changes from R1:

1. **Anonymous users may add attachments.** R1's `authedUid()` check that returned `null` for anonymous users in `buildMaintenanceLogPath` is gone — `addPickedFile` works regardless of auth state because all it does is write to `filesDir`. The upload scheduler is the gate: uploads only fire when `AuthManager.authState.value is Permanent`. Anonymous users see `LocalOnly` indefinitely until they link their account; on link, the scope is unchanged (R1 §7.3) so all their `LOCAL_ONLY` blobs become eligible.

2. **`AuthManager.observeAuthState` cancels in-flight uploads on sign-out.** On `Anonymous → SignedOut` or `Permanent → SignedOut`, `UploadScheduler.cancelAll()` runs. Any `UPLOADING` rows revert to `LOCAL_ONLY` on the next driver tick.

---

## 9b. Quota & duplicate enforcement

Three checks, all enforced **client-side in the picker**, before a `PendingAttachment.Local` is added to the form's pending list. They run in this order so the cheapest rejection happens first:

| Check | Value / rule | Source of truth |
|---|---|---|
| 1. Per-parent duplicate | Reject if `sha256(candidateBytes)` matches another non-LINK attachment on this parent (`Local` or `Saved`, excluding `PendingDelete`) | the form's own pending list — sha256s for `Local` items are already computed; for `Saved` items they're in the proto |
| 2. Per-parent size | 25 MB summed across `Local` + `Saved` non-LINK attachments on that parent | the form's own pending list — no DB query needed |
| 3. Per-user size | 1 GB summed across the user's scope | `SELECT SUM(size_bytes) FROM blob_object WHERE scope_path LIKE :scopePrefix AND deleted = 0` |

The dedupe check requires sha256, so the picker reads the candidate bytes and computes the hash *before* deciding whether to accept. That work is not wasted: if accepted, those same bytes (and that same hash) are handed straight to `LocalBlobStore.put` — see §6.3.

### `QuotaChecker` (commonMain, `feature/attachment/datamanager`)

```kotlin
class QuotaChecker(
  private val blobs: LocalBlobStore,
  private val authState: StateFlow<AuthState>,
) {
  data class State(
    val perUserUsedBytes: Long,
    val perUserCapBytes: Long = USER_CAP_BYTES,  // 1 GB
  ) {
    val perUserRemaining: Long get() = (perUserCapBytes - perUserUsedBytes).coerceAtLeast(0)
  }

  fun observeState(): Flow<State>           // recomputes when blob_object changes

  /**
   * Returns Allowed if the candidate file fits both caps and is not already on the parent;
   * otherwise a typed rejection.
   *
   * @param candidateSha256        hex sha256 of the candidate bytes (caller computes once and re-uses)
   * @param candidateBytes         length of the candidate file
   * @param parentNonLinkSha256s   sha256 hashes of every non-LINK attachment already on this parent
   *                               (Local + Saved, excluding PendingDelete) — caller derives from form state
   * @param pendingBytesOnParent   sum of size_bytes for those same non-LINK attachments
   */
  suspend fun check(
    candidateSha256: String,
    candidateBytes: Long,
    parentNonLinkSha256s: Set<String>,
    pendingBytesOnParent: Long,
  ): QuotaResult
}

sealed class QuotaResult {
  data object Allowed : QuotaResult()
  data class DuplicateOnParent(val sha256: String) : QuotaResult()
  data class PerParentExceeded(val capBytes: Long, val wouldBeBytes: Long) : QuotaResult()
  data class PerUserExceeded(val capBytes: Long, val usedBytes: Long, val candidateBytes: Long) : QuotaResult()
}
```

The form ViewModel (`MaintenanceLogFormViewModel`, etc.) reads the picked bytes via `FileByteReader`, computes sha256 once, then calls `quotaChecker.check(...)` synchronously before adding to its pending list. On rejection it surfaces an inline error string from `feature/attachment/sharedassets`. On acceptance, the same bytes + sha256 are passed to `LocalBlobStore.put` so the hash is not recomputed.

### Why count `REMOTE_ONLY` against the per-user cap

`blob_object` rows of state `REMOTE_ONLY` have `size_bytes` populated (from the proto's `size_bytes` field). Including them in the per-user sum means the cap is **device-independent**: a user near 1 GB on their phone sees the same rejection on a fresh-install second device once it has pulled their entity rows. If we excluded `REMOTE_ONLY`, a user could keep adding past 1 GB by picking up a fresh device.

### Defining the per-user sum precisely

```sql
SELECT COALESCE(SUM(size_bytes), 0) AS used FROM blob_object
WHERE scope_path = :userScope AND deleted = 0;
```

`deleted = 0` excludes tombstoned rows (the gs delete worker hasn't run yet but the row is on its way out). This means the "used" number drops as soon as the user removes an attachment, even before the gs:// object is freed — matches user intuition.

### Reactive UI

`QuotaChecker.observeState()` is a Flow backed by a SQLDelight `selectUsedBytes` query. The Settings → Storage screen uses it to render a progress bar ("832 MB of 1 GB used"). The picker bottom sheet renders the same headline so the user sees their headroom at the moment of choice.

### Server-side defense

Per the PRD (N6), Firebase Storage rules can reject a single PUT > 25 MB:

```
match /users/{uid}/aircraft/{aircraftId}/blobs/{blobId} {
  allow write: if request.auth.uid == uid
            && request.resource.size < 25 * 1024 * 1024;
}
```

The 1 GB per-user cap cannot be expressed in Storage rules without an aggregate read. We accept that the user-cap is a soft cap — a malicious or modded client could exceed it. For a personal logbook app this is a reasonable trade-off; if it ever matters, a Cloud Function can audit per-user totals on a schedule and revoke writes for offenders.

---

## 10. Existing-attachment handling — clean break

R1-era attachments (proto has `download_url` populated, `sha256` empty) are **not migrated** and **not openable** in R2. This is the deliberate one-time break called out in §1.

What R2 does on encountering a legacy attachment:

1. **`BlobIndexReconciler` skips them.** It only creates `REMOTE_ONLY` rows for attachments with `sha256.isNotBlank()`.
2. **`AttachmentOpener` shows an inline error** for legacy rows: "This attachment was added in an older version and is no longer accessible. Re-attach the file to restore access." `OpenState.Failed(LegacyAttachment)`.
3. **The viewing list still renders the legacy row** (name + size + a greyed-out icon) so the user sees what was there. Tapping shows the error above instead of opening.
4. **The picker accepts a re-attach** — pick the same file again and a new R2-shaped `Attachment` is created with a new `id`. The legacy row can be removed by the user normally; on remove, the legacy gs:// object is **not** deleted (R2's delete worker keys on `blob_object`, which has no row for it). Orphaned legacy bytes sit in Firebase Storage until manually cleaned via the console, or via a one-shot Cloud Function in a follow-up.

### Why no migration shim

A migration shim would have to: re-fetch every legacy file via its `download_url`, compute sha256, copy bytes to the new path scheme, and rewrite the proto. That's network-heavy, can fail per-file, and requires tracking state across attempts. Given the user base is small and the R1 attachment feature has been live a short time, the cost of breakage is low and the simplification is high. The decision is captured here so a future engineer doesn't try to add the shim and re-introduce the back-compat surface area.

### Release-notes communication

The R2 release ships with an in-app announcement on first run for users with at least one legacy attachment: "Attachments are getting an upgrade. Attachments added before this version are no longer accessible — please re-attach any files you still need." Implementation: a one-time Snackbar on the maintenance/inspection screens when any visible entity has a legacy attachment, dismissible.

---

## 11. Testing

### 11.1 `LocalBlobStore` contract tests

A `LocalBlobStoreContract` base class (mirroring the R1 `EntityStoreContract` pattern):

- `put` writes file + row; `get` returns matching `BlobRef`.
- `installDownloaded` with mismatched sha256 returns `Failure`, leaves state `REMOTE_ONLY`, file not written.
- `installDownloaded` with matching sha256 flips state to `SYNCED` and `localUri` returns the file URI.
- `delete` after `SYNCED` removes the local file but row stays with `deleted=1` until hard-deleted by the upload driver after gs delete succeeds.
- Two scopes are isolated.
- State transitions are valid (`LOCAL_ONLY → UPLOADING → SYNCED`, `REMOTE_ONLY → SYNCED`); invalid transitions throw.
- Sealed-subclass coverage assertion: `RemoteState::class.sealedSubclasses.size == columnAdapter.knownStates.size`.

### 11.2 Upload/download driver tests (`kmm-test-writer`)

- **Upload happy path:** `LOCAL_ONLY` row + bytes → `runOnce` → mocked `FirebaseStorage.putData` succeeds → row `SYNCED`, `remote_path` populated.
- **Upload transient failure:** mocked `putData` throws `IOException` → row reverts to `LOCAL_ONLY`, `upload_attempts=1`, `last_attempt_at` set.
- **Upload permanent failure:** mocked `putData` throws auth error → row stays `LOCAL_ONLY`, `Failed` status surfaces via `observeStatus`.
- **Download verification:** mocked Firebase Storage returns bytes whose sha256 matches → file written, `SYNCED`. Mismatch → no file written, error.
- **Cancel-on-sign-out:** `UPLOADING` row + scheduler `cancelAll()` → next driver tick reverts to `LOCAL_ONLY`.
- **Reconciler insert:** seed `entity` row with proto containing 2 non-LINK attachments + 1 LINK → reconciler runs → 2 `REMOTE_ONLY` rows inserted, LINK ignored, no row for already-known id (`INSERT OR IGNORE`).
- **Reconciler orphan GC:** entity referencing blob deleted → reconciler at next cold start tombstones the now-orphaned blob.

### 11.3 Integration on a single device

- Add 3 attachments offline (anonymous user) → all `LOCAL_ONLY` → sign in with Google (link) → uploads complete → all `SYNCED`.
- Sign in on second device → entity sync delivers protos → 3 `REMOTE_ONLY` rows appear → tap one → downloaded + verified + opens.
- Delete an attachment on device A → device B (offline) → device B reconnects → orphan GC tombstones B's row, gs:// object already deleted by A's `BlobDeleteDriver`.

### 11.4 Property-test on the state machine

A small property test that takes a sequence of `(operation, simulated network outcome)` pairs and asserts the row terminates in a legal state. Cheap insurance against state-machine regressions during M7 polish.

---

## 12. Rollout

R2 is a single user-facing release; M4 and M5 must ship together (PRD §11.1). M7 polish (iOS background URLSession) ships a follow-up version.

No feature flag — R2 is a clean break (§10) and there is no R1 path to keep alive in parallel. Each PR below leaves `main` shippable on its own.

1. **PR 1 — schema + `feature/attachment` scaffold + LocalBlobStore.** Add `blob_object` table + queries to `core/storage/Schema.sq`; add `RemoteState` sealed type + `ColumnAdapter` to `core/storage`. Scaffold the new `feature/attachment` module with the canonical layout (model/datamanager/sharedassets/viewing/update) and Gradle wiring. Implement `LocalBlobStore` interface + `SqlDelightLocalBlobStore` impl + `BlobFilesystem` expect/actual + contract tests in `feature/attachment/datamanager`. The old `core/attachments` module is **untouched** in this PR — its Koin binding still serves `AttachmentManager`. Mergeable as inert infra.
2. **PR 2 — proto changes.** Add `sha256 = 10` to `Attachment`; mark field 5 (`download_url`) reserved. Wire-generate. Old proto still decodes (the deleted field is ignored on the wire) but R1 builds reading R2-written attachments will see no `download_url` — this is the break. Document in the release notes.
3. **PR 3 — `QuotaChecker` + AttachmentManager rewrite + `core/attachments` deletion.** Implement `LocalFirstAttachmentManagerImpl`, `QuotaChecker`, and the new `AttachmentManager` interface in `feature/attachment/datamanager`. Move `AttachmentRow`/`AttachmentSection` from `core/attachments/viewing` into `feature/attachment/viewing`; move strings/icons into `feature/attachment/sharedassets`. Move `PendingAttachment` from `core/attachments/model` into `feature/attachment/model`. Move the picker bottom sheet into `feature/attachment/update`. **Delete the `core/attachments` module entirely** and update `settings.gradle.kts` + `composeApp/di/initKoin.kt`. ViewModels move to the synchronous `addPickedFile` flow. Picker calls `QuotaChecker.check` before adding to pending list.
4. **PR 4 — opener.** `AttachmentOpener` rewritten in `feature/attachment/datamanager` to consult `LocalBlobStore`. Legacy attachments surface `Failed(LegacyAttachment)`.
5. **PR 5 — upload scheduler + drivers + reconciler.** In `feature/sync/data/blob/`: Android `WorkManagerUploadScheduler`; iOS `ForegroundUploadScheduler`; `BlobUploadDriver`/`BlobDownloadDriver`/`BlobDeleteDriver`; `BlobIndexReconciler` hooked into `PullListener`. Settings → Backup & Sync gains the **"Allow upload on cellular"** toggle (default OFF, §13.1). PR 5 is the first one where new attachments actually round-trip end-to-end.
6. **PR 6 — Settings / status surfaces.** Per-attachment status badge in viewing UI; Settings → Storage screen showing per-user usage progress; "Remove this account from this device" wipe action (§13.4); legacy-attachment one-time Snackbar (§10).
7. **PR 7 (M7) — iOS URLSession background + integrity-check recovery.** Replaces `ForegroundUploadScheduler` with `UrlSessionUploadScheduler` on iOS; adds `PRAGMA integrity_check` recovery flow per PRD §10.

Internal dogfood after PR 6 for 1–2 weeks → staged rollout 10% → 50% → 100%.

Per-PR regression watch:
- Firestore reads/writes — should be unchanged (attachments don't add proto traffic).
- Firebase Storage egress — drops as `REMOTE_ONLY` placeholders avoid eager fetch; uploads are unchanged.
- `filesDir` size — new; expected to be the user's attachment total. Display in Settings ("Attachment cache: 240 MB") in M6/R2.

---

## 13. Resolved decisions

1. **Cellular-upload default — WiFi-only.** A new toggle in **Settings → Backup & Sync → "Allow upload on cellular"** defaults **OFF**. When OFF, `WorkManagerUploadScheduler` enforces `NetworkType.UNMETERED`; the iOS scheduler short-circuits if the OS reports a cellular network. Toggling ON is read live by both schedulers (no app restart required). Surface in the same Settings section as the existing "Enable Sync" toggle (R1/M6) so all sync-cost controls live together.

2. **Per-blob retention cap — none.** Bounded by the per-user 1 GB cap (§9b) by construction; no LRU eviction needed.

3. **Image compression — defer to R3.** The user's condition was "lossless AND interoperable across both platforms." No image format meets all three: JPEG/HEIC are lossy by nature; PNG re-encoding gains little for already-compressed photos; WebP-lossless is interoperable on Android but non-native on iOS Photos. Compressing only some formats would surprise users (their JPEG stays full-size, their PNG shrinks), so we ship R2 with **no compression**, matching the existing decision. Re-evaluate in R3 when format usage telemetry is available — if PNG dominates we can re-encode losslessly; if JPEG/HEIC dominate, the answer stays "no."

4. **Wipe on "remove account from this device" — immediate.** When the user invokes the wipe action: hard-delete every `blob_object` row whose `scope_path` matches the removed uid AND delete every file at `filesDir/blobs/{id}` for those rows. No tombstone, no soft-delete. The cloud copy is intact, so a subsequent sign-in re-runs hydration as if the device were fresh. Implementation lives in `feature/attachment/datamanager` alongside the existing entity-wipe (`UploadScheduler.cancelForUid(uid)` runs first to abort any in-flight uploads). Document the action's irreversibility in the confirmation dialog: "All locally-stored attachments will be removed from this device. Files in the cloud are unaffected."

---

## 14. Summary

R2 turns attachments into first-class local-first data:

- A `blob_object` table (in `core/storage`) + `LocalBlobStore` (in `feature/attachment/datamanager`) mirrors the proto-level `entity` machinery from R1.
- The pre-canonical `core/attachments` module is deleted; all attachment code moves into `feature/attachment` with the canonical model/datamanager/sharedassets/viewing/update layout.
- Saves no longer block on uploads; the scheduler does the work in the background.
- Anonymous users can attach files; on link, their blobs upload under their now-permanent uid (zero migration).
- New devices see lazy `REMOTE_ONLY` placeholders and download on demand, with sha256 integrity verification.
- The proto gains `sha256` and reserves the old `download_url` field — clean break with R1; no migration shim, no fall-through.
- Two caps + dedupe enforced client-side: 25 MB per parent, 1 GB per user, no duplicate sha256 on the same parent.
- WiFi-only uploads by default; "Allow upload on cellular" toggle in Settings → Backup & Sync.
- "Remove this account from this device" wipes local blobs immediately; cloud copy intact.
- iOS ships foreground-only initially; URLSession background lands in M7.

Estimated work: ~2 weeks single-engineer for M4+M5, plus 1 week for M7 polish, plus a 1-week staged rollout. Each PR in §12 is independently mergeable.
