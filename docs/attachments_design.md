# Design Doc: Attachments for Maintenance Logs and Inspection Items

> **Status / supersession.** This is the **original** (pre-local-first) attachment design — it assumes logs live
> directly in Firestore (`users/{uid}/fleet/{aircraftId}/...`). The **storage and sync mechanism here is
> superseded** by the local-first R2 design in [`storage_r2_design.md`](storage_r2_design.md), which is what
> actually shipped (`feature/attachment/` + `core/storage` `blob_object` + blob drivers in
> `feature/sync/data/blob/`, gated behind the `attachmentUploadEnabled` feature-lab flag). The **product
> requirements** below (supported file types, size caps, attach-during-form UX) remain largely valid.

## Context

Maintenance logs are stored as protobuf blobs in Firestore at:
```
users/{uid}/fleet/{aircraftId}/maintenance_logs/{logId}
  log_info_blob: bytes   ← MaintenanceLog proto
  timestamp, component_type, … (queryable native fields)
```

Inspection cards follow the same pattern:
```
users/{uid}/fleet/{aircraftId}/inspection_cards/{cardId}
  inspection_info_blob: bytes   ← InspectionCard proto
```

The current `MaintenanceLog` proto already has an unused `repeated string attachment_urls = 9`. This field will be repurposed by reusing field 9's slot with a structured `repeated Attachment` message. **This is only safe because no data was ever written to field 9.** If any client had previously persisted strings at field 9, reusing the field number would produce corrupt decodes (wire type 2 would be reinterpreted as a nested message). The safety assumption must be verified before migration — do not treat this as a general protobuf feature. `InspectionCard` has no existing attachment field.

---

## Data Model

### New protobuf — `attachment.proto` (in `core/model`)

```proto
syntax = "proto3";

import "google/protobuf/timestamp.proto";

option java_package = "dev.fanfly.wingslog.aircraft";
option java_multiple_files = true;

enum AttachmentType {
  ATTACHMENT_TYPE_UNKNOWN = 0;
  ATTACHMENT_TYPE_IMAGE   = 1;   // image/jpeg, image/png, image/heic, …
  ATTACHMENT_TYPE_PDF     = 2;   // application/pdf
  ATTACHMENT_TYPE_LINK    = 3;   // external URL, no stored file
  ATTACHMENT_TYPE_FILE    = 4;   // catch-all (text, csv, …)
}

message Attachment {
  string id            = 1;  // UUID, generated client-side
  string name          = 2;  // display name; defaults to filename or URL domain
  AttachmentType type  = 3;
  string storage_path  = 4;  // Firebase Storage object path; empty for LINK type
  string download_url  = 5;  // persisted at upload time — avoid repeated getDownloadUrl() calls
  string url           = 6;  // populated for LINK type; empty for file types
  string mime_type     = 7;  // e.g. "image/jpeg"
  int64  size_bytes    = 8;  // 0 for links
  google.protobuf.Timestamp created_at = 9;
}
```

### Changes to existing protos

**`maintenance_log.proto`** — replace the unused field 9 (safe only because no data was ever written to this field — see Context):
```proto
// was: repeated string attachment_urls = 9;
repeated Attachment attachments = 9;
```

**`inspection_card.proto`** — add new field:
```proto
repeated Attachment attachments = 13;
```

---

## Storage Layout (Firebase Storage)

```
users/{uid}/
  fleet/{aircraftId}/
    maintenance_logs/{logId}/{attachmentId}_{sanitised_filename}
    inspection_cards/{cardId}/{attachmentId}_{sanitised_filename}
```

**Key decisions:**

- The path encodes the parent type (maintenance_logs vs inspection_cards), aircraftId, and the parent document ID. This means the path is known **before** the Firestore document is saved, because IDs are generated client-side via `generateRandomId()` — already the case for log saves.
- `{attachmentId}_{sanitised_filename}` avoids collisions between multiple attachments with the same filename on the same parent and makes the object self-describing in the Firebase Storage console.
- Hyperlinks (`ATTACHMENT_TYPE_LINK`) are stored only in the protobuf; nothing is written to Firebase Storage.

**Why not a subcollection for attachment metadata?**

The existing pattern is one Firestore document = one protobuf blob. Introducing an `attachments` subcollection would require:
- A separate Firestore read every time a log or card is loaded.
- Deletion of N subcollection documents on parent delete (no atomic batch delete in Firestore without Cloud Functions).
- A more complex ViewModel that combines two async streams.

Embedding the `repeated Attachment` metadata in the parent protobuf keeps reads as a single document fetch and deletions as a single document delete (plus Firebase Storage object cleanup).

**Download URL strategy:**

`download_url` is fetched once via `FirebaseStorage.getDownloadUrl(storagePath)` immediately after upload and stored in the `Attachment` message inside the protobuf. This avoids calling `getDownloadUrl()` on every view. If the URL is ever invalidated (e.g. file re-uploaded), the viewer calls `AttachmentManager.getDownloadUrl(storagePath)` as a fallback. The ViewModel that triggered the open is responsible for writing the refreshed URL back to Firestore — the `AttachmentManager` interface has no reference to the parent document, so the re-write must be handled at the call site (e.g. `MaintenanceLogManager.updateLogAttachmentUrl`). Without this, the fallback only fixes the current session.

---

## Module Design

Following the canonical feature module pattern, attachments are a cross-cutting concern shared by `feature/logs` and `feature/tasks`. They belong in `feature/attachment/`, and follow the same layered structure used in feature modules:

```
feature/
  attachment/
    model/          ← AttachmentStatus, AttachmentWithState, BlobSyncState, PendingAttachment
    datamanager/    ← AttachmentManager interface + LocalBlobStore impl + Koin module
    sharedassets/   ← strings ("Attachments", "Add attachment", …), type icons
    viewing/        ← AttachmentRow, AttachmentThumbnail, AttachmentSection (read-only, stateless)
```

The picker sheet (edit-time UI) lives in the consuming feature's `update` module, not in `feature/attachment/viewing`, because it requires ViewModel interaction. Shared strings and icons used by both picker and viewer belong in `feature/attachment/sharedassets/`.

The `Attachment` proto message itself lives in `core/model` alongside the other proto messages (same as today — all protos compile there).

### `AttachmentManager` interface (`feature/attachment/datamanager`)

```kotlin
interface AttachmentManager {
  /**
   * Upload a local file and emit progress until the upload completes.
   * Terminal emission is Done(attachment) with storage_path and download_url populated,
   * or Failed(error) if the upload cannot be completed.
   */
  fun uploadFile(
    storagePath: String,
    localUri: String,
    mimeType: String,
    displayName: String,
  ): Flow<UploadState>

  /**
   * Delete a file from Firebase Storage. No-op for LINK type attachments.
   */
  suspend fun deleteFile(attachment: Attachment): Result<Unit>

  /**
   * For cases where download_url is stale or missing — re-fetches from Firebase Storage.
   */
  suspend fun getDownloadUrl(storagePath: String): Result<String>
}

sealed class UploadState {
  data class Uploading(val progress: Float) : UploadState()  // 0f..1f
  data class Done(val attachment: Attachment) : UploadState()
  data class Failed(val error: Throwable) : UploadState()
}
```

The original `suspend fun uploadFile(…): Result<Attachment>` signature is replaced with a `Flow<UploadState>` so that upload progress can be surfaced in the form UI while the upload is in flight (required by N2 in the PRD).

`AttachmentManagerImpl` uses `dev.gitlive.firebase.storage.FirebaseStorage` (already in the GitLive KMP SDK dependency).

### `AttachmentStoragePath` helper (`feature/attachment/datamanager`)

Centralises path construction so both maintenance log and task builds use identical, predictable paths:

```kotlin
object AttachmentStoragePath {
  fun forMaintenanceLog(uid: String, aircraftId: String, logId: String, attachmentId: String, filename: String): String =
    "users/$uid/fleet/$aircraftId/maintenance_logs/$logId/${attachmentId}_${filename.sanitise()}"

  fun forInspectionCard(uid: String, aircraftId: String, cardId: String, attachmentId: String, filename: String): String =
    "users/$uid/fleet/$aircraftId/inspection_cards/$cardId/${attachmentId}_${filename.sanitise()}"

  private fun String.sanitise() = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
```

---

## Platform-Specific: File Picker

File picking and reading local URI bytes requires `expect/actual`:

```kotlin
// commonMain
expect class FilePicker {
  suspend fun pickFiles(): List<PickedFile>
}

data class PickedFile(
  val uri: String,
  val name: String,
  val mimeType: String,
  val sizeBytes: Long,
)
```

- **androidMain**: `ActivityResultContracts.OpenMultipleDocuments(arrayOf("*/*"))` wrapped in a coroutine using `rememberLauncherForActivityResult`.
- **iosMain**: `UIDocumentPickerViewController` for files; `PHPickerViewController` for photos. Exposed via KMP expect/actual using a callback-to-coroutine bridge.

The `FilePicker` is injected via Koin as a platform-specific singleton.

---

## Add/Edit Flow — ViewModel State

Both `MaintenanceLogFormViewModel` and `InspectionViewModel` gain a list of `PendingAttachment`:

```kotlin
sealed class PendingAttachment {
  /** Picked locally, not yet uploaded. */
  data class Local(
    val tempId: String,
    val localUri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
  ) : PendingAttachment()

  /** Already persisted (loaded from existing log/card). */
  data class Saved(val attachment: Attachment) : PendingAttachment()

  /** Marked for deletion on next save. */
  data class PendingDelete(val attachment: Attachment) : PendingAttachment()
}
```

### Attachment limits enforced in the picker

- **Files**: maximum 3 per log/card (across `Local` + `Saved` items, excluding `PendingDelete`). The "Choose file" button is disabled and shows "Maximum 3 files reached" when the count is at 3.
- **Links**: unlimited.
- **Per-parent total size**: 25 MB summed across all file attachments on a single log/card (links don't count). Computed from `PickedFile.sizeBytes` (pending) + `Attachment.size_bytes` (saved) before adding. If a new file would push the parent over 25 MB, show an inline error: "Adding this file would exceed the 25 MB limit for this entry."
- **Per-user total storage**: 1 GB summed across all of the user's attachments (every log + every inspection card combined). Computed from `blob_object.size_bytes` summed across the user's scope (counts both `LOCAL_ONLY` and `REMOTE_ONLY` rows so the cap is consistent regardless of which device the user is on). If exceeded, show an inline error: "You've reached the 1 GB attachment limit. Remove an attachment before adding more." See `storage_r2_design.md` §9b for enforcement details.
- **Per-parent duplicate**: a file whose sha256 matches another non-LINK attachment already on the parent (`Local` or `Saved`, excluding `PendingDelete`) is rejected before it joins the pending list. Show an inline error: "This file is already attached to this entry." Sha256 is computed from the picked bytes — the same hash used for the integrity check on download. Renaming the file on disk does not bypass the check; matching the byte content does. Links are never deduplicated (two different display names pointing at the same URL are allowed).

### Add-link UX

The picker sheet "Add link" option presents an inline text field. On confirmation:
- Validate that the input is a well-formed URL (`Url.parse` or `URI` constructor); show an inline error if not.
- Default `name` to the URL's host+path truncated at 40 characters (e.g. `rgl.faa.gov/Regulatory…`).
- Allow the user to edit the name before confirming.
- Empty or whitespace-only URLs must be rejected before adding to the pending list.

### Save sequence

```
User taps Save
  │
  ├─ 1. Upload all PendingAttachment.Local items in parallel
  │      attachmentManager.uploadFile(storagePath, localUri, …)
  │        .collect { state -> when (state) { is Uploading -> updateProgress(); is Done -> …; is Failed -> … } }
  │      On failure: delete any files successfully uploaded in this batch → return error
  │
  ├─ 2. Delete all PendingAttachment.PendingDelete items
  │      coroutineScope { pendingDeletes.map { launch { attachmentManager.deleteFile(it) } }.joinAll() }
  │      Log errors; do not block save on Storage deletion failures.
  │
  ├─ 3. Build final Attachment list:
  │      (Saved items) + (newly uploaded items)
  │
  └─ 4. Save parent document (MaintenanceLog / InspectionCard) with updated attachments list
         Same existing save path — the attachments field is just populated now
```

Step 4 is the existing `MaintenanceLogManager.addLog/updateLog` or `TaskDataManager.addTask/updateTask` — no change to those interfaces. The caller populates `log.attachments` before calling the manager.

**Why upload before save (not after)?** The parent document stores `download_url` in the attachment metadata. The URL is only known post-upload, so the upload must precede the Firestore write. The parent document is never saved in a state where it has a `storage_path` but no `download_url`.

---

## View Flow — Opening Attachments

There is no in-app viewer of any kind. All files are downloaded to the device and handed off to the OS; the OS decides which app opens them. Links open in the default browser. This keeps the app simple and lets the platform handle format support, zooming, sharing, etc.

### Platform abstraction

```kotlin
// commonMain
expect class AttachmentOpener {
  /**
   * For LINK type: open attachment.url in the system browser.
   * For all file types: download attachment.download_url to a local path,
   * then hand off to the OS native open mechanism.
   * Emits OpenState so the caller can show a progress indicator.
   */
  fun open(attachment: Attachment): Flow<OpenState>
}

sealed class OpenState {
  object Downloading : OpenState()              // download in progress
  object Done        : OpenState()              // OS handed off successfully
  data class Failed(val error: Throwable) : OpenState()
}
```

`AttachmentOpener` is injected via Koin as a platform-specific singleton. The handler lives in the screen composable (or ViewModel) that hosts the detail view; `AttachmentSection` and `AttachmentRow` are stateless and receive `onTap: (Attachment) -> Unit` as a lambda.

### Android implementation

**Links:**
```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attachment.url))
context.startActivity(intent)
```

**Files:** Use the system `DownloadManager` service. This is the right tool for the job: it runs in a separate process, continues if the app is backgrounded, shows a system notification with progress, and puts the file in the Downloads folder so the user can find it again.

```kotlin
val request = DownloadManager.Request(Uri.parse(attachment.download_url))
  .setTitle(attachment.name)
  .setMimeType(attachment.mime_type.ifEmpty { "*/*" })
  .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
  .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.name)
val downloadId = downloadManager.enqueue(request)

// Listen for completion via BroadcastReceiver on ACTION_DOWNLOAD_COMPLETE,
// then open the downloaded file:
val uri = downloadManager.getUriForDownloadedFile(downloadId)
val intent = Intent(Intent.ACTION_VIEW).apply {
  setDataAndType(uri, attachment.mime_type.ifEmpty { "*/*" })
  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(intent, null))
```

`DownloadManager` requires `WRITE_EXTERNAL_STORAGE` on API < 29 and `android.permission.DOWNLOAD_WITHOUT_NOTIFICATION` — both standard and auto-granted on the target SDK.

### iOS implementation

**Links:**
```swift
UIApplication.shared.open(URL(string: attachment.url)!!)
```

**Files:** Download to the app's temp directory using `URLSession`, then open with `UIDocumentInteractionController`:
```swift
let tempUrl = FileManager.default.temporaryDirectory
  .appendingPathComponent(attachment.name)
let (data, _) = try await URLSession.shared.data(from: URL(string: attachment.download_url)!!)
try data.write(to: tempUrl)

let controller = UIDocumentInteractionController(url: tempUrl)
controller.presentPreview(animated: true)  // uses QuickLook: PDF, images, etc.
// Falls back to open-with sheet if QuickLook can't handle the type.
```

iOS temp directory is cleaned up by the OS; no manual cache management needed.

### Stale `download_url`

Firebase Storage download URLs can expire or be revoked. If the download fails with a 403:
- `DownloadManager` / `URLSession` will surface an HTTP error to the user via a system notification or the `OpenState.Failed` emission.
- The app shows a snackbar: "Could not open attachment. Try again."
- On retry, call `attachmentManager.getDownloadUrl(storagePath)` first to get a fresh URL, use that for the download, and silently write the refreshed URL back to the proto in Firestore as a background fire-and-forget.

This is only needed on retry — the happy path trusts the stored `download_url`.

---

## View Flow — Attachment List Composables

These live in `feature/attachment/viewing` and are stateless. No Coil dependency — the list uses type icons only; there is no in-app rendering of file contents.

### `AttachmentRow`

Each attachment is a full-width tappable row with a type icon, name, and subtitle.

```
┌──────────────────────────────────────────────────┐
│  [icon]  Service Bulletin SB-23-15                │
│          PDF · 340 KB                    [↗ open] │
└──────────────────────────────────────────────────┘
```

```kotlin
@Composable
fun AttachmentRow(
  attachment: Attachment,
  onTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onTap(attachment) }
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = attachment.typeIcon(),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = attachment.name,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = attachmentSubtitle(attachment),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.OpenInNew,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
  }
}

private fun Attachment.typeIcon() = when (type) {
  ATTACHMENT_TYPE_PDF   -> Icons.Outlined.PictureAsPdf
  ATTACHMENT_TYPE_LINK  -> Icons.Outlined.Link
  ATTACHMENT_TYPE_IMAGE -> Icons.Outlined.Image
  else                  -> Icons.Outlined.InsertDriveFile
}

private fun attachmentSubtitle(attachment: Attachment): String = when (attachment.type) {
  ATTACHMENT_TYPE_LINK -> attachment.url.toDisplayDomain()  // e.g. "rgl.faa.gov"
  else -> "${attachment.mimeTypeLabel()} · ${attachment.size_bytes.toFileSize()}"
}
```

### `AttachmentSection`

Used in `InspectionDetailSheet` and the maintenance log detail view. Hidden when the list is empty (per PRD F3/F4).

```kotlin
@Composable
fun AttachmentSection(
  attachments: List<Attachment>,
  onAttachmentTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (attachments.isEmpty()) return

  Column(modifier = modifier) {
    Text(
      text = stringResource(SharedRes.string.attachments),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(Spacing.small))
    attachments.forEach { attachment ->
      AttachmentRow(attachment = attachment, onTap = onAttachmentTap)
      HorizontalDivider()
    }
  }
}
```

`AttachmentSection` is added below the existing content in `InspectionDetailSheet` and below the work description in the maintenance log detail view, separated by `Spacer(Modifier.height(Spacing.large))`.

---

## Upload and Download Reliability

### What the Firebase Storage SDK handles for you

- **Automatic retry on transient failures.** The native Firebase Storage SDKs (wrapped by GitLive) retry with exponential backoff on network blips. You do not need to write retry logic.
- **Resumable (multipart) uploads.** The SDK switches to multipart upload automatically for files above ~5 MB. If the connection drops mid-upload the SDK retries the in-flight chunk — it does not restart from byte 0. This is transparent.
- **Resumable downloads (Android DownloadManager).** `DownloadManager` uses HTTP range requests internally; if a download is interrupted it resumes from where it left off.

### What you do need to handle

**Uploads:**

| Scenario | What happens | What to build |
|---|---|---|
| Network blip during upload | SDK retries automatically | Nothing |
| Persistent network loss | SDK eventually fails the upload | `UploadState.Failed` → show error + Retry button |
| User taps Save and immediately backgrounds the app | Upload coroutine is cancelled if the process is killed by Android | Acceptable for V1. Show error on return; user re-taps Save. |
| User backgrounds app on iOS | iOS suspends the app; upload likely fails | Same as above. |
| Upload partially succeeds (some files done, one fails) | Save sequence aborts and cleans up already-uploaded files | Already handled in the save sequence (step 1 rollback) |

**Background uploads are not worth building for V1.** A proper background upload service requires `WorkManager` + a `ForegroundService` on Android, and a background `URLSession` transfer on iOS. That is significant platform-specific complexity for a personal logbook app where saves are short, intentional actions with the user present. If a user backgrounds the app mid-save and the process is killed, they get an error on return and can save again — this is acceptable.

**Downloads (opening attachments):**

| Scenario | What happens | What to build |
|---|---|---|
| Network blip | `DownloadManager` / `URLSession` retries automatically | Nothing |
| Persistent failure | System download fails; user gets a system notification | `OpenState.Failed` → snackbar "Could not open — check connection" |
| User backgrounds app mid-download (Android) | `DownloadManager` is a system service and continues independently | Nothing — this is free |
| User backgrounds app mid-download (iOS) | `URLSession` download is paused; resumes when app returns to foreground | Acceptable |
| Stale `download_url` (403) | Download fails | On retry, call `getDownloadUrl(storagePath)` first; write refreshed URL back in background |
| File already downloaded | `DownloadManager` downloads again to Downloads folder | Acceptable for V1 |

**Large files (up to 25 MB limit):**
- Progress is surfaced automatically by `DownloadManager` via system notification on Android.
- On iOS, emit `OpenState.Downloading` while `URLSession` is in flight and show a loading indicator in the UI.
- The 25 MB cap means worst-case ~3 min on a poor connection — acceptable since the user initiates the open deliberately.

### Summary

For a personal logbook app with a 25 MB file size cap, you get retry and resumability for free from the platform. The only thing to build is: progress indicators, an error state with a retry button, and stale-URL refresh on retry. Do not build a background upload service for V1.

---

## Deletion of Parent Document

When a maintenance log or inspection card is deleted, its associated Firebase Storage files must also be deleted.

**Approach:** The existing `deleteLog` and `deleteInspection` methods are extended to:
1. Fetch the document's current `Attachment` list (already in memory in the ViewModel that triggered the delete, passed as a parameter).
2. Delete all non-link attachments in parallel; log errors but do not block the Firestore delete on Storage failures.
3. Proceed with the Firestore document delete.

```kotlin
suspend fun deleteLog(aircraftId: String, log: MaintenanceLog): Result<Boolean> {
  coroutineScope {
    log.attachments
      .filter { it.type != AttachmentType.ATTACHMENT_TYPE_LINK }
      .map { launch { attachmentManager.deleteFile(it) } }
  }
  return deleteLogDocument(aircraftId, log.id)
}
```

This is best-effort: if Storage deletion fails, the Firestore document is still deleted and orphaned Storage objects are acceptable (no data loss for the user; storage can be cleaned up via Firebase console or a future Cloud Function). Errors from `deleteFile` are logged, not silently swallowed.

---

## Dependency Graph (additions only)

```
core/model                          ← Attachment proto message added here
feature/attachment/datamanager      ← AttachmentManager, LocalBlobStore, AttachmentOpener (expect)
  depends on: core/model, core/storage, Firebase Storage, Koin
feature/attachment/sharedassets     ← strings (attachments label, add attachment, …), type icons
  depends on: Compose resources only
feature/attachment/viewing          ← AttachmentRow, AttachmentSection (read-only, no Coil)
  depends on: core/model, feature/attachment/sharedassets, core/ui, Compose

feature/logs                   ← adds dep on feature/attachment/viewing
                                  picker sheet lives in update submodule
feature/tasks/viewing         ← adds dep on feature/attachment/viewing
feature/tasks/update          ← picker sheet lives here; adds dep on feature/attachment/datamanager
```

`feature/logs/datamanager` and `feature/tasks/datamanager` gain the `AttachmentManager` as an injected dependency (for file deletion on parent delete).

---

## Decisions

1. **Max attachment count per item.** Maximum **3 uploaded files** per log/card. Hyperlinks are unlimited. The picker disables the "Choose file" option and shows an inline message ("Maximum 3 files reached") once the file count hits 3; the "Add link" option remains available.

2. **Per-parent size cap.** **25 MB total** across all file attachments on a single log/card. Enforced in the picker before adding a file to the pending list (sum pending + saved sizes; reject if the new file would exceed 25 MB).

3. **Per-user storage quota.** **1 GB** total across all of a user's attachments. Enforced client-side from the local `blob_object` table (summed across the user's scope). On the server side, document the cap; defense-in-depth via Firebase Storage rules is a future hardening — the PRD states this is a soft cap until storage rules can express it.

4. **Image compression.** None. Files are uploaded as-is.

5. **Anonymous users.** R2 lifts the R1 restriction — anonymous users may add attachments. Bytes are stored locally; uploads to Firebase Storage happen only after the account is linked to a permanent provider. See `storage_r2_design.md` §9.

6. **Firebase Storage pricing.** Free tier: 5 GB storage, 1 GB/day download. The 1 GB-per-user app cap keeps each individual user under the project's free-tier headroom even with a small population. Document in the release notes that attachments count against project storage quota.
