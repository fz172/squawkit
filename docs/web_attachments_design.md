# Web Attachments Design

**Status:** In progress. M0–M4 have landed. M5 (flip the UI gate to
`attachmentUploadEnabled`) and M6 (hardening / release checks) remain.

The bucket needs a one-time CORS rule before web downloads work —
`backend/firebase/storage_cors.json` is the checked-in rule set; see
`backend/firebase/README.md` for the `gcloud storage buckets update` command.

**Companion docs:** [`storage_r2_design.md`](storage_r2_design.md),
[`attachments_PRD.md`](attachments_PRD.md), [
`web_target_expansion_plan.md`](web_target_expansion_plan.md).

## 1. Goal

Enable the existing R2 attachment feature on `webApp` without creating a browser-specific
attachment model:

- Logs, tasks, and squawks use the existing shared `AttachmentFormSection`, `AttachmentSection`,
  `AttachmentManager`, `LocalBlobStore`, and `blob_object` state machine.
- Browser bytes are local-first: picked files are stored under the origin, referenced from
  `blob_object`, and uploaded later by the same common `BlobUploadDriver`.
- Remote-only attachments hydrate as placeholders and download on open, using the existing
  `BlobDownloadDriver` and sha256 verification.
- Links remain metadata-only and open in a new browser tab.

Out of scope for the first web shipment:

- Service-worker background transfer after the tab closes.
- Drag-and-drop and paste-to-upload. The first web picker is button-driven.
- Attachment thumbnails. Rows keep the existing type-icon presentation.
- Anonymous attachment support on web. Web auth intentionally requires a real account.

## 2. Current Gaps

The code is already prepared to compile with attachments disabled:

- `webApp/src/jsMain/kotlin/dev/fanfly/wingslog/web/WebApp.kt` passes
  `attachmentsAvailable = false` to aircraft overview, logs, tasks, and squawks.
- `feature/attachment/datamanager/src/jsMain/.../AttachmentModule.js.kt` binds
  `DisabledWebAttachmentManager` and `DisabledWebAttachmentOpener` instead of the common
  local-first manager.
- `feature/attachment/viewing/src/jsMain/.../FilePicker.kt` returns a no-op picker.
- `feature/sync/data/src/jsMain/.../BlobSchedulerModule.js.kt` binds no scheduler.
- `Sha256.js.kt` throws because Web Crypto is async while `sha256Hex(bytes)` is synchronous.

The web design should replace those stubs with platform implementations while preserving the
shared contracts.

## 3. Browser Blob Storage

### 3.1 Storage backend

Use the Origin Private File System (OPFS) for attachment bytes, separate from the SQLite OPFS file:

```text
OPFS root
  hopply/
    blobs/
      {attachmentId}.bin
```

Rationale:

- The web app already requires OPFS-capable browsers for durable SQLite.
- Blob bytes can be larger than normal entity rows; keeping them out of SQLite preserves the R2
  split used on mobile.
- OPFS is origin-private and not directly user-browsable, matching Android/iOS app-private storage.

Implement `OpfsBlobFilesystem : BlobFilesystem` in
`feature/attachment/datamanager/src/jsMain/...`:

- `write(relativePath, bytes)` creates parent directories and writes with OPFS file handles.
- `read(relativePath)` returns a `ByteArray`.
- `delete(relativePath)` removes the file and tolerates missing files.
- `exists(relativePath)` probes the file handle.
- `uriFor(relativePath)` should return a diagnostic pseudo-URI such as
  `opfs://hopply/blobs/{id}.bin`; web opening must not depend on this value because generating a
  real `blob:` URL requires async file reads.

The actual opener reads bytes through `BlobFilesystem.read(...)` and creates object URLs itself.

### 3.2 Picked-file byte handoff

Browser `File` objects are not stable OS file handles. After a user picks a file, the app must keep
the bytes in memory long enough for `AttachmentManager.addPickedFile(...)` to copy them into OPFS.

Add a small JS-only in-memory registry:

```kotlin
internal object WebPickedFileRegistry {
  fun put(bytes: ByteArray): String       // returns "web-picked:{id}"
  fun take(uri: String): ByteArray?
  fun clear(uri: String)
}
```

`rememberFilePicker` reads each selected `File.arrayBuffer()` immediately, stores the bytes in this
registry, and emits `PickedFile(uri = "web-picked:{id}", name, mimeType, sizeBytes)`.
`WebFileByteReader.readBytes(uri)` consumes the registry entry. This keeps the common
`FileByteReader` interface unchanged and ensures form save is the moment bytes become durable in
OPFS.

If a form is abandoned, the in-memory registry entries may be dropped by a simple best-effort
timeout or by an explicit `clear` call from the picker callback path. They are not durable until
the user saves.

## 4. Hashing

Do not rework the common hash API to suspend just for web. Use a synchronous JS SHA-256
implementation for `Sha256.js.kt`, for example an npm dependency such as `js-sha256`, wrapped by a
small Kotlin/JS adapter that accepts `ByteArray` / `Uint8Array` and returns lowercase hex.

This preserves:

- `SqlDelightLocalBlobStore.put(...)` computing sha256 synchronously after `fs.write`.
- `installDownloaded(...)` verifying downloaded bytes without a platform branch.
- Existing Android/JVM tests and iOS actuals.

## 5. Upload, Download, and Delete Scheduling

Web gets a foreground scheduler. It does not attempt service-worker persistence in v1.

Implement `ForegroundWebBlobScheduler : UploadScheduler` in
`feature/sync/data/src/jsMain/.../blob/`:

- Inject `BlobUploadDriver`, `BlobDownloadDriver`, and `BlobDeleteDriver`.
- Maintain a `MutableSet<BlobId>` per operation to coalesce duplicate schedules.
- Launch work in a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- On upload/download transient failure, retry while the tab is open with exponential backoff:
  `30s, 60s, 120s ... max 60min`.
- `cancelAll()` cancels the scope's active jobs and recreates it.

Startup scanning is required because a user can reload while local rows are pending:

- When Koin starts, schedule all `LOCAL_ONLY` rows for the signed-in user's scope.
- Schedule tombstoned rows for delete.
- Do not auto-download `REMOTE_ONLY` rows; keep lazy download-on-open semantics.

To support this cleanly, either add focused `LocalBlobStore` methods for pending rows or expose
query methods from the scheduler module using existing `schemaQueries`. Prefer adding narrow
`LocalBlobStore` methods if Android/iOS can use them too.

## 6. Firebase Storage I/O

The common blob drivers should remain the transfer boundary:

- Upload: `BlobUploadDriver` already reads bytes from `BlobFilesystem` and calls
  `FirebaseStorage.reference(remotePath).putData(...)`.
- Download: `BlobDownloadDriver` already resolves a Storage download URL and reads via Ktor.
- Delete: `BlobDeleteDriver` already deletes the Storage object and hard-deletes the row.

The design assumes GitLive Firebase Storage JS supports the same common calls. If `putData` or
download URL behavior differs on JS, keep the public driver contract and isolate the JS-specific
Firebase call behind a small platform adapter rather than branching in feature ViewModels.

## 7. Opening Attachments

Implement `AttachmentOpenerWeb`:

- Link attachments: normalize missing schemes to `https://` and call
  `window.open(url, "_blank", "noopener,noreferrer")`.
- Local/Synced attachments: read `blobRelativePath(attachment.id)` from `BlobFilesystem`, create a
  `Blob` with `attachment.mime_type`, create an object URL, and open it in a new tab.
- `REMOTE_ONLY` or missing indexed rows: collect `attachmentManager.ensureLocal(attachment)` until
  terminal, then read and open the downloaded bytes.
- Legacy attachments with blank `sha256`: emit `OpenState.Failed(LegacyAttachment())`, matching
  mobile.

Object URLs should be revoked after a short delay, not immediately after `window.open`, so the new
tab has time to load the bytes.

## 8. UI Enablement

After the web platform pieces are bound:

- Register the common `attachmentModule` in `webApp/src/jsMain/kotlin/main.kt`; keep
  `platformAttachmentModule` for JS-only `BlobFilesystem`, `FileByteReader`, and
  `AttachmentOpenerWeb`.
- Register `blobSchedulerModule` with `ForegroundWebBlobScheduler`.
- Change the web routes in `WebApp.kt` to pass the same feature-flag-gated availability used by
  mobile, rather than hardcoded `false`.

The simplest first pass is:

```kotlin
attachmentsAvailable = true
```

for web dogfood builds once the `attachmentUploadEnabled` flag is enabled. A cleaner follow-up is
to flow `FeatureFlags` into `WebApp` and pass `flags.attachmentUploadEnabled`, matching mobile
behavior exactly.

## 9. Quotas and Limits

Keep all current R2 limits:

- 3 files per parent entry.
- 25 MB per parent entry.
- 1 GB per user.
- duplicate rejection by sha256 on the same parent.

The browser picker should reject files before writing to the in-memory registry when cheap checks
are possible:

- `File.size` can reject obvious per-parent/per-user overflow before reading bytes.
- sha256 duplicate detection still requires reading bytes; after hashing, reuse the same bytes for
  `WebPickedFileRegistry` so the file is not read twice.

No web-specific quota should be introduced. If OPFS quota exhaustion occurs despite app-level
limits, surface it as the existing picker/read error and leave the parent entity unchanged.

## 10. Failure Modes

| Failure                           | Behavior                                                                                 |
|-----------------------------------|------------------------------------------------------------------------------------------|
| Browser lacks OPFS                | Disable attachments and show the existing unavailable state; entity sync can still run.  |
| User closes tab during upload     | Row remains `LOCAL_ONLY`; startup scan retries next visit.                               |
| User closes tab during download   | Row remains `REMOTE_ONLY`; opening again retries.                                        |
| OPFS write fails                  | `addPickedFile` fails before parent save; no attachment proto is added.                  |
| Firebase upload fails transiently | Row returns to `LOCAL_ONLY`, upload attempts increment, row shows upload failed/pending. |
| Download sha mismatch             | Delete candidate bytes, keep row `REMOTE_ONLY`, show integrity error.                    |
| Popup/new-tab blocked             | Emit `OpenState.Failed`; keep bytes and row state unchanged.                             |

## 11. Implementation Milestones

The implementation should land in independently buildable slices. Keep
`attachmentsAvailable = false` until M4 finishes; earlier milestones are infrastructure only and
should not expose partial UI.

### M0 — Compile-safe foundation

Goal: replace the current "throws on web" primitives with real implementations that can be tested
without enabling the attachment UI.

Work:

- Add synchronous JS sha256 in `Sha256.js.kt`, preferably via a small npm dependency such as
  `js-sha256`.
- Add JS tests for known SHA-256 vectors.
- Keep `DisabledWebAttachmentManager`, disabled picker, and empty scheduler bindings in place.

Exit criteria:

- `:feature:attachment:datamanager:jsBrowserTest` passes.
- Android/iOS attachment tests remain green.

### M1 — Local browser byte storage

Goal: make picked browser bytes durable in OPFS through the existing `LocalBlobStore` contract, but
still without upload/download or visible UI.

Work:

- Add `WebPickedFileRegistry` for short-lived selected-file bytes.
- Implement `WebFileByteReader` over the registry.
- Implement `OpfsBlobFilesystem`.
- Change JS `platformAttachmentModule` to bind `BlobFilesystem` and `FileByteReader`, but keep the
  disabled `AttachmentManager` until the storage path is tested.
- Add focused tests for `OpfsBlobFilesystem` when practical; if browser OPFS is hard to unit-test in
  Gradle, add a minimal in-browser diagnostic route or debug action for manual verification.

Exit criteria:

- Writing bytes through `BlobFilesystem.write`, reading them back, reloading the page, and reading
  again works in a browser.
- OPFS failure leaves no `blob_object` row, matching mobile ordering.

### M2 — Local add and local open

Goal: allow the common `AttachmentManager` and opener to work for locally picked files and links,
without remote transfer.

Work:

- Bind the common `attachmentModule` on web instead of `DisabledWebAttachmentManager`.
- Implement real JS `rememberFilePicker`, returning `PickedFile(uri = "web-picked:{id}", ...)`.
- Implement `AttachmentOpenerWeb` for links and local `LOCAL_ONLY` / `SYNCED` bytes.
- Keep `blobSchedulerModule` empty, so new files remain `LOCAL_ONLY` after save.
- Keep web routes passing `attachmentsAvailable = false`; use a debug-only entry point or tests to
  exercise manager/opener behavior before exposing production UI.

Exit criteria:

- `AttachmentManager.addPickedFile(...)` creates an `Attachment` proto, writes OPFS bytes, and
  inserts a `LOCAL_ONLY` `blob_object` row.
- A local attachment can be opened from OPFS via a generated `blob:` URL.
- Links open in a new tab and never create blob rows.

### M3 — Foreground sync workers

Goal: connect browser-local rows to Firebase Storage using the existing common blob drivers.

Work:

- Bind `BlobUploadDriver`, `BlobDownloadDriver`, and `BlobDeleteDriver` for JS if they are not
  already reachable through `syncModule`.
- Implement `ForegroundWebBlobScheduler`.
- Add startup scanning for pending uploads and tombstoned rows.
- Confirm GitLive Firebase Storage JS supports the required `putData`, `getDownloadUrl`, and
  `delete` calls. If not, add a small platform adapter under the driver boundary.

Exit criteria:

- A `LOCAL_ONLY` row uploads to Firebase Storage and transitions to `SYNCED`.
- A tombstoned synced row deletes its remote object and hard-deletes the local row.
- Reloading the page while a row is `LOCAL_ONLY` schedules it again after sign-in.

### M4 — Remote-only download and open

Goal: support the second-device path before making the UI generally available.

Work:

- Verify `BlobIndexReconciler` creates `REMOTE_ONLY` rows on web when synced protos hydrate.
- Wire `AttachmentOpenerWeb` through `attachmentManager.ensureLocal(...)` for `REMOTE_ONLY` rows.
- Confirm `BlobDownloadDriver` downloads bytes, verifies sha256, writes OPFS bytes, and transitions
  to `SYNCED`.

Exit criteria:

- Browser profile A uploads an attachment.
- Browser profile B hydrates the parent entity, sees `REMOTE_ONLY`, opens the attachment, downloads
  and verifies it, then opens the local object URL.
- A sha256 mismatch keeps the row `REMOTE_ONLY` and surfaces an integrity failure.

### M5 — UI enablement

Goal: expose attachments on web behind the same product gate as mobile.

Work:

- Change web routes in `WebApp.kt` away from hardcoded `attachmentsAvailable = false`.
- Prefer passing `FeatureFlags.attachmentUploadEnabled`; a dogfood-only `true` is acceptable only as
  a short-lived bring-up step.
- Verify logs, tasks, and squawks all use the same behavior. Do not enable one parent type
  permanently while the others remain disabled unless there is a documented product reason.

Exit criteria:

- With `attachmentUploadEnabled` off, web behaves as it does today.
- With the flag on, users can add, view, open, delete, and retry attachments from the normal web UI.
- Quota and duplicate errors use existing shared copy and do not introduce web-only limits.

### M6 — Hardening and release checks

Goal: close the browser-specific reliability gaps before considering web attachments generally
available.

Work:

- Test refresh/close behavior during pending upload and pending download.
- Test OPFS quota or write-denied behavior where the browser allows simulation.
- Test popup-blocked open behavior.
- Confirm no object URLs leak during repeated open operations.
- Confirm Storage paths and `scope_path` values match the current R2 aircraft-child scope:
  `/users/{uid}/aircraft/{aircraftId}/` locally and
  `users/{uid}/aircraft/{aircraftId}/blobs/{attachmentId}` remotely.

Exit criteria:

- Add/open/upload/download/delete works in a running browser with reloads between each state.
- A populated mobile account can open its remote attachments from web.
- A web-uploaded attachment opens from mobile after sync.

## 12. Verification

Build checks:

```bash
./gradlew :feature:attachment:datamanager:jsBrowserTest
./gradlew :feature:attachment:viewing:jsBrowserTest
./gradlew :feature:sync:data:jsBrowserTest
./gradlew :webApp:jsBrowserDevelopmentWebpack
```

Browser checks:

- Pick a PDF on web, save a maintenance log, reload, and confirm the attachment row remains
  `LOCAL_ONLY` or `SYNCED` rather than disappearing.
- With network/auth available, confirm upload transitions to `SYNCED` and the Firebase Storage path
  matches `users/{uid}/aircraft/{aircraftId}/blobs/{attachmentId}` semantics used by current R2
  scope construction.
- Sign in on a second browser profile, hydrate the same log, confirm the attachment starts
  `REMOTE_ONLY`, opens after download, and verifies sha256.
- Delete the attachment, reload, and confirm the row and OPFS bytes are gone.
