# Design Doc: Attachments for Maintenance Logs and Inspection Items

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

Following the canonical feature module pattern, attachments are a cross-cutting concern shared by `feature/maintenance` and `feature/inspection`. They belong in `core/`, and follow the same layered structure used in feature modules:

```
core/
  attachments/
    model/          ← AttachmentType enum, any domain wrappers around the proto
    datamanager/    ← AttachmentManager interface + Firebase Storage impl + Koin module
    sharedassets/   ← strings ("Attachments", "Add attachment", …), type icons
    viewing/        ← AttachmentRow, AttachmentThumbnail, AttachmentSection (read-only, stateless)
```

The picker sheet (edit-time UI) lives in the consuming feature's `update` module, not in `core/attachments/viewing`, because it requires ViewModel interaction. Shared strings and icons used by both picker and viewer belong in `core/attachments/sharedassets/`.

The `Attachment` proto message itself lives in `core/model` alongside the other proto messages (same as today — all protos compile there).

### `AttachmentManager` interface (`core/attachments/datamanager`)

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

### `AttachmentStoragePath` helper (`core/attachments/datamanager`)

Centralises path construction so both maintenance and inspection build identical, predictable paths:

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

Step 4 is the existing `MaintenanceLogManager.addLog/updateLog` or `InspectionManager.addInspection/updateInspection` — no change to those interfaces. The caller populates `log.attachments` before calling the manager.

**Why upload before save (not after)?** The parent document stores `download_url` in the attachment metadata. The URL is only known post-upload, so the upload must precede the Firestore write. The parent document is never saved in a state where it has a `storage_path` but no `download_url`.

---

## View Flow — Opening Attachments

```kotlin
fun onAttachmentTap(attachment: Attachment, navController: NavController, platformContext: PlatformContext) {
  when (attachment.type) {
    ATTACHMENT_TYPE_IMAGE -> navController.navigate(ImageViewerRoute(attachment.download_url, attachment.name))
    ATTACHMENT_TYPE_LINK  -> platformContext.openUrl(attachment.url)
    ATTACHMENT_TYPE_PDF,
    ATTACHMENT_TYPE_FILE  -> platformContext.openWithSystemViewer(attachment.download_url, attachment.mime_type)
  }
}
```

This handler lives in the screen composable (or ViewModel) that hosts the detail view. `AttachmentSection` and `AttachmentRow` are stateless and receive `onTap: (Attachment) -> Unit` as a lambda.

For `openWithSystemViewer`:
- **Android**: `Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).setType(mimeType)` — passes the Firebase Storage download URL directly to the system. No local download required; Android can stream the file.
- **iOS**: `UIApplication.shared.open(URL(string: downloadUrl))` — same approach.

**Image viewer:** A dedicated full-screen route `AttachmentImageViewerScreen`, not a `ModalBottomSheet`. Stacking a bottom sheet on top of an existing bottom sheet (`InspectionDetailSheet`) is fragile on both platforms and breaks the back-stack. The screen renders:
- `AsyncImage` filling the screen with `ContentScale.Fit`, using `memoryCacheKey = downloadUrl` so Coil reuses the thumbnail already loaded in `AttachmentRow`.
- A single close/back button (top-left); no app bar chrome.
- `placeholder` set to the generic image icon from `core/attachments/sharedassets`.

**Stale `download_url` fallback:** If the initial image load fails, the viewer calls `attachmentManager.getDownloadUrl(storagePath)`. On success, the ViewModel writes the refreshed URL back to Firestore via the parent document manager and re-triggers the image load. This is a background coroutine — the UI shows a loading indicator, not an error, while the refresh is in flight.

---

## View Flow — Attachment List Composables

These live in `core/attachments/viewing` and are stateless.

### `AttachmentRow`

Each attachment is a full-width tappable row. Images show a 48×48dp thumbnail loaded by Coil; all other types show a type icon.

```
┌──────────────────────────────────────────────────┐
│  [thumb/icon]  Service Bulletin SB-23-15          │
│                PDF · 340 KB              [↗ open] │
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
    AttachmentThumbnail(attachment, size = 48.dp)
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

private fun attachmentSubtitle(attachment: Attachment): String = when (attachment.type) {
  ATTACHMENT_TYPE_LINK -> attachment.url.toDisplayDomain()  // e.g. "rgl.faa.gov"
  else -> "${attachment.mimeTypeLabel()} · ${attachment.size_bytes.toFileSize()}"
}
```

### `AttachmentThumbnail`

```kotlin
@Composable
private fun AttachmentThumbnail(attachment: Attachment, size: Dp) {
  val shape = RoundedCornerShape(6.dp)
  if (attachment.type == ATTACHMENT_TYPE_IMAGE && attachment.download_url.isNotEmpty()) {
    AsyncImage(
      model = ImageRequest.Builder(LocalContext.current)
        .data(attachment.download_url)
        .memoryCacheKey(attachment.download_url)
        .build(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(size).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
  } else {
    Box(
      modifier = Modifier.size(size).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = attachment.typeIcon(),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

private fun Attachment.typeIcon() = when (type) {
  ATTACHMENT_TYPE_PDF   -> Icons.Outlined.PictureAsPdf
  ATTACHMENT_TYPE_LINK  -> Icons.Outlined.Link
  ATTACHMENT_TYPE_IMAGE -> Icons.Outlined.Image
  else                  -> Icons.Outlined.InsertDriveFile
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
core/model                    ← Attachment proto message added here
core/attachments/datamanager  ← AttachmentManager, UploadState, AttachmentStoragePath
  depends on: core/model, Firebase Storage, Koin
core/attachments/sharedassets ← strings (attachments label, add attachment, …), type icons
  depends on: Compose resources only
core/attachments/viewing      ← AttachmentRow, AttachmentThumbnail, AttachmentSection (read-only)
  depends on: core/model, core/attachments/sharedassets, core/ui, Compose, Coil

feature/maintenance            ← adds dep on core/attachments/viewing
                                  picker sheet lives here (update submodule)
feature/inspection/viewing    ← adds dep on core/attachments/viewing
feature/inspection/update     ← picker sheet lives here; adds dep on core/attachments/datamanager
```

`feature/maintenance/database` and `feature/inspection/datamanager` gain the `AttachmentManager` as an injected dependency (for file deletion on parent delete).

---

## Open Questions / Decisions Needed

1. **Max attachment count per item.** Recommend 10 for V1. Beyond that the embedded proto grows large and the save-time upload becomes slow.
2. **Max file size.** Firebase Storage has no enforced limit, but large uploads on mobile are poor UX. Recommend a 25 MB soft limit enforced in the picker with a user-facing error.
3. **Image compression.** Should picked images be compressed before upload? Reduces cost and upload time at the expense of some quality. Recommend yes, compress to max 2048px and 85% JPEG quality for `ATTACHMENT_TYPE_IMAGE`.
4. **Anonymous users.** Anonymous users can currently create logs. Firebase Storage security rules must be decided: allow anonymous uploads (storage billed to the project) or require sign-in. Recommendation: require sign-in to upload files; show a prompt to sign in if the user tries to add an attachment while anonymous.
5. **Firebase Storage pricing.** Free tier: 5 GB storage, 1 GB/day download. For a personal logbook this is more than sufficient. Document in the release notes that attachments count against project storage quota.
6. **`download_url` refresh write-back.** Decide which manager method handles writing a refreshed URL back to Firestore when the stale-URL fallback fires (e.g. `MaintenanceLogManager.updateLogAttachmentUrl` / `InspectionManager.updateCardAttachmentUrl`). Must be defined before implementing the image viewer.
