# R2 Design: Local-First Attachments

**Branch:** `r2-attachments-design`
**PRD:** [`storage_mode_PRD.md`](storage_mode_PRD.md) — §6, §11.1 R2
**Companion:** [`storage_r1_design.md`](storage_r1_design.md) — R1 sets the proto-sync foundation this doc builds on.
**Scope:** M4 (local blob store + AttachmentManager rewrite + opener) + M5 (upload scheduler + lazy download) + the R2 portion of M7 (iOS background `URLSession`, integrity-check recovery).

---

## 1. Goals & non-goals

**In:**
- All attachments — files, images, PDFs — are written to the device first (`filesDir/blobs/{id}`) and uploaded to Firebase Storage in the background.
- Attachments added offline are usable on that device immediately and upload when network/auth allow.
- Anonymous users can attach files. The R1 anonymous-blocked path is removed.
- New devices for an existing account see attachment placeholders (`REMOTE_ONLY`) and download bytes lazily on first open.
- A binary integrity check (`sha256`) on every download.

**Out:**
- Per-device attachment encryption (existing Firebase Storage rules suffice).
- CRDT-style merging — attachments are immutable; conflict reduces to *exists / doesn't exist*.
- Block-level dedupe across attachments (sha256 dedupe is a future optimisation).
- Video files (deferred per attachments PRD §Non-Goals).

---

## 2. Module layout

One new module, plus targeted edits inside `core/attachments/datamanager` and `core/storage`.

```
core/
  storage/                                 # M1 — extended in R2
    src/commonMain/sqldelight/.../db/
      Schema.sq                            #   adds blob_object table + queries (§4)
    src/commonMain/kotlin/.../core/storage/blob/
      BlobId.kt                            #   value class
      BlobRef.kt                           #   row projection used by callers
      RemoteState.kt                       #   sealed interface + ColumnAdapter
      LocalBlobStore.kt                    #   interface
      impl/
        SqlDelightLocalBlobStore.kt        #   filesystem + DB row
        BlobFilesystem.kt                  #   expect class — filesDir, mkdir, write/read/delete
    src/androidMain/kotlin/.../blob/impl/
      BlobFilesystem.android.kt            #   Context.filesDir
    src/iosMain/kotlin/.../blob/impl/
      BlobFilesystem.ios.kt                #   NSDocumentDirectory + NSURLIsExcludedFromBackupKey

  attachments/                             # rewritten in R2
    datamanager/
      AttachmentManager.kt                 #   interface — see §6
      impl/
        LocalFirstAttachmentManagerImpl.kt #   replaces AttachmentManagerImpl
      AttachmentOpener.kt                  #   now resolves via LocalBlobStore (§7)

feature/
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

`composeApp/di/initKoin.kt` swaps the Koin binding for `AttachmentManager` from R1's Firebase-direct impl to the local-first impl, and adds `blobUploadModule` to the aggregate.

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
  remote_path="users/{uid}/blobs/att-7", deleted=0
```

`blob_object` is **device-local only** — it is never synced. The proto carries everything another device needs (id, sha256, size, mime, name) to recreate a `REMOTE_ONLY` row when it sees the proto.

This split is the core lemma of M4+M5: the attachments PRD's existing flows already populate the proto; we add a sibling table that mirrors only what the device needs to manage bytes.

### Why not extend the `entity` table?

`entity` rows participate in proto sync (push / pull / LWW). Blobs do not — they don't have a Firestore document of their own; the binary lives in Firebase Storage and the metadata rides inside the owning entity's proto. Putting blob state into `entity` would require either (a) a `payload_schema = 'blob_object_state'` row that we never sync (a special case), or (b) adding sync-suppression columns. A second table with no sync columns is simpler.

### Proto changes (`attachment.proto`)

Add `sha256` and `blob_size` (already covered by `size_bytes`, kept) so a remote device can construct a placeholder row without an extra round-trip:

```proto
message Attachment {
  string id            = 1;
  string name          = 2;
  AttachmentType type  = 3;
  string storage_path  = 4;  // gs://users/{uid}/blobs/{id} after R2
  string download_url  = 5;  // legacy; populated only by R1 writers (kept for read compat)
  string url           = 6;
  string mime_type     = 7;
  int64  size_bytes    = 8;
  google.protobuf.Timestamp created_at = 9;
  string sha256        = 10; // NEW — hex; required for non-LINK after R2
}
```

`download_url` is never written by R2 clients (file URI is local; remote URI is fetched on demand). It is still read for back-compat with attachments that were written by R1 builds — see §10 (migration).

`storage_path` is normalised to `users/{uid}/blobs/{id}` (no per-aircraft subpath). This decouples attachment location from the owning entity, so an attachment's bytes don't move when the user later edits which log it's attached to. Old per-aircraft paths continue to resolve via the legacy `download_url` field; we never rewrite them.

### Why `sha256` in the proto and not just the `blob_object` row?

A new device that only has the proto needs to know the expected sha256 *before* it has downloaded the bytes — otherwise on download we cannot verify integrity, and the placeholder row cannot be populated with anything other than `null`. Putting the hash in the proto is one extra ~64-byte string per attachment; trivial and correct.

---

## 4. SQLDelight schema (`Schema.sq` additions)

```sql
import dev.fanfly.wingslog.core.storage.blob.RemoteState;

CREATE TABLE blob_object (
  id             TEXT    NOT NULL PRIMARY KEY,    -- attachment id; same as Attachment.id in proto
  scope_path     TEXT    NOT NULL,                -- '/users/{uid}/' (uid that owns the blob)
  relative_path  TEXT    NOT NULL,                -- 'blobs/{id}.bin' under filesDir
  content_type   TEXT,                            -- e.g. 'image/jpeg'; null acceptable
  size_bytes     INTEGER NOT NULL,
  sha256         TEXT    NOT NULL,                -- hex
  remote_state   TEXT    AS RemoteState NOT NULL,
  remote_path    TEXT,                            -- 'users/{uid}/blobs/{id}' once SYNCED, null otherwise
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

The "uploading" indicator the R1 form showed during save becomes a per-attachment badge in the viewing UI (a small cloud-with-up-arrow icon while `Uploading`, plain icon when `Synced`).

---

## 7. `AttachmentOpener` changes

Today (R1) the opener takes the `download_url` from the proto and hands it to `DownloadManager` / `URLSession`. R2 routes through `LocalBlobStore`:

```
opener.open(attachment) →
  if attachment.type == LINK: open attachment.url in system browser  (unchanged)
  else:
    blob = blobStore.get(attachment.id)
    when blob.remoteState:
      LOCAL_ONLY, SYNCED, UPLOADING → open file:// at filesDir/blobs/{id}
      REMOTE_ONLY                   → emit Downloading; download via Firebase Storage SDK;
                                       blobStore.installDownloaded(); on success emit Done; open file://
      null (no row)                 → if proto has legacy download_url (R1 attachment), use it directly;
                                       BlobIndexReconciler will create the row on next entity sync
```

The "legacy download_url" branch is the migration ramp — it lets R2 builds open R1-era attachments without needing a one-shot rewriter. As soon as the owning entity is re-saved on R2, the proto picks up `sha256` + the new `storage_path` shape and the row is created on the next pull elsewhere.

### Stale download URL on legacy attachments

Same as R1: if the legacy `download_url` 403s, fall back to `FirebaseStorage.getDownloadUrl(storage_path)`. We do **not** rewrite `download_url` in the proto on R2 — the proto's authoritative reference for new clients is `id` + `sha256`, fetched through the SDK.

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

1. For each attachment with `type != LINK` and `id` not present in `blob_object`:
   - `blobStore.upsertRemoteOnly(id, sha256, size_bytes, mime_type, scope)`
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

The split lets R2 ship with full Android background support and iOS-foreground; neither blocks the other. The "Upload on cellular" toggle from §6.3 of the PRD is a single `Boolean` pref read by both schedulers.

---

## 9. Auth integration

Two changes from R1:

1. **Anonymous users may add attachments.** R1's `authedUid()` check that returned `null` for anonymous users in `buildMaintenanceLogPath` is gone — `addPickedFile` works regardless of auth state because all it does is write to `filesDir`. The upload scheduler is the gate: uploads only fire when `AuthManager.authState.value is Permanent`. Anonymous users see `LocalOnly` indefinitely until they link their account; on link, the scope is unchanged (R1 §7.3) so all their `LOCAL_ONLY` blobs become eligible.

2. **`AuthManager.observeAuthState` cancels in-flight uploads on sign-out.** On `Anonymous → SignedOut` or `Permanent → SignedOut`, `UploadScheduler.cancelAll()` runs. Any `UPLOADING` rows revert to `LOCAL_ONLY` on the next driver tick.

---

## 10. Existing-attachment migration

Every attachment written by R1 has:
- A populated `download_url` and `storage_path` (per-aircraft path).
- No `sha256`.

On first R2 launch, we do **not** run a rewriter. Instead:

1. **Reads continue to work.** `AttachmentOpener` still honours `download_url` for any attachment it sees with no `blob_object` row.
2. **`BlobIndexReconciler` skips legacy attachments.** It only inserts `REMOTE_ONLY` rows for attachments where `sha256.isNotBlank()`. Legacy ones don't get a row; they remain "URL-only" forever or until their owning entity is re-saved.
3. **Re-save upgrades.** If a user edits a maintenance log on an R2 build, the form picks up its existing attachments as `Saved`, **does not** alter them, and the entity push leaves the proto bytes unchanged for those (no upgrade). New attachments added in the same edit go through the R2 path. Old + new co-exist in one entity.
4. **Optional cleanup migration (R3).** A future build could run a one-shot worker that re-reads each legacy attachment's bytes via its `download_url`, computes sha256, and rewrites the proto with the R2 shape. Out of scope for R2 — the dual-read path is enough.

The dual-read path means R2 has zero migration risk. If `BlobIndexReconciler` is buggy, only freshly-added attachments are affected.

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

1. **PR 1 — schema + LocalBlobStore.** New `blob_object` table, `RemoteState` sealed type + adapter, `LocalBlobStore` interface + `SqlDelightLocalBlobStore` impl, `BlobFilesystem` expect/actual, contract tests. Untouched: AttachmentManager, openers, sync. Mergeable behind no flag.
2. **PR 2 — proto changes.** Add `sha256 = 10` to `Attachment`. Wire-generate. R1 readers ignore the new field; R1 writers don't populate it. Mergeable behind no flag.
3. **PR 3 — AttachmentManager rewrite.** New `LocalFirstAttachmentManagerImpl`. Koin binding behind a `localFirstAttachments` build-time flag (default off in main, on in dogfood). Old impl kept compiling for the off case. ViewModels switch to the new save flow shape (synchronous `addPickedFile`). Hidden behind the same flag.
4. **PR 4 — opener routing.** `AttachmentOpener` consults `LocalBlobStore` first; falls through to legacy URL on no-row.
5. **PR 5 — upload scheduler + drivers + reconciler.** Android WorkManager scheduler; iOS foreground scheduler; `BlobIndexReconciler` hooked into `PullListener`. Behind the same flag.
6. **PR 6 — flip the flag.** Internal dogfood for 1–2 weeks → staged rollout 10% → 50% → 100%.
7. **PR 7 (M7) — iOS URLSession background + integrity-check recovery.** Replaces `ForegroundUploadScheduler` with `UrlSessionUploadScheduler` on iOS; adds `PRAGMA integrity_check` recovery flow per PRD §10.

Per-PR regression watch:
- Firestore reads/writes — should be unchanged (attachments don't add proto traffic).
- Firebase Storage egress — drops as `REMOTE_ONLY` placeholders avoid eager fetch; uploads are unchanged.
- `filesDir` size — new; expected to be the user's attachment total. Display in Settings ("Attachment cache: 240 MB") in M6/R2.

---

## 13. Open questions

1. **Cellular-upload default.** PRD says WiFi-only by default. Confirm; this is a UX choice — pilots in remote airfields often have cellular only. Recommend default WiFi-only with a prominent "Upload on cellular" toggle, so the user controls cost.
2. **Per-blob retention cap.** Should we cap `filesDir/blobs` (e.g. 2 GB) and evict `SYNCED` blobs LRU? For R2 v1, no — typical logbook is well under 1 GB. Revisit if telemetry shows footprint creep.
3. **Image compression.** PRD §Decisions in `attachments_design.md` says no compression. Reconfirm for R2 — it's a one-line decision but easy to forget when pictures land.
4. **Re-key on uid change.** If a user runs "remove account from this device" with R2 attachments present, do we delete the local files immediately or just tombstone? Recommend: immediate (the action is a wipe, and the cloud copy is intact).

---

## 14. Summary

R2 turns attachments into first-class local-first data:

- A `blob_object` table + `LocalBlobStore` mirrors the proto-level `entity` machinery from R1.
- Saves no longer block on uploads; the scheduler does the work in the background.
- Anonymous users can attach files; on link, their blobs upload under their now-permanent uid (zero migration).
- New devices see lazy `REMOTE_ONLY` placeholders and download on demand, with sha256 integrity verification.
- The proto gains one field (`sha256`) and the URL fields are kept for back-compat. No SQL migration touches existing tables; only adds `blob_object`.
- iOS ships foreground-only initially; URLSession background lands in M7.

Estimated work: ~2 weeks single-engineer for M4+M5, plus 1 week for M7 polish, plus a 1-week staged rollout. Each PR in §12 is independently testable; the flag in PRs 3–5 lets internal builds exercise the local-first path before public rollout.
