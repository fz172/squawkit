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

The current `MaintenanceLog` proto already has an unused `repeated string attachment_urls = 9`. This field will be replaced (repurposed by reusing field 9's slot) with a structured `repeated Attachment` message. `InspectionCard` has no existing attachment field.

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

**`maintenance_log.proto`** — replace the unused field 9:
```proto
// was: repeated string attachment_urls = 9;
repeated Attachment attachments = 9;
```
Wire handles the in-place replacement cleanly because the field number is reused and old clients that wrote empty string lists will decode to an empty Attachment list (no stored data existed).

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

`download_url` is fetched once via `FirebaseStorage.getDownloadUrl(storagePath)` immediately after upload and stored in the `Attachment` message inside the protobuf. This avoids calling `getDownloadUrl()` on every view. If the URL is ever invalidated (e.g. file re-uploaded), the stored URL will fail and the app falls back to `getDownloadUrl()` on demand.

---

## Module Design

Following the canonical feature module pattern, attachments are a cross-cutting concern shared by `feature/maintenance` and `feature/inspection`. They belong in `core/`:

```
core/
  attachments/
    model/          ← AttachmentType enum, any domain wrappers around the proto
    datamanager/    ← AttachmentManager interface + Firebase Storage impl + Koin module
    ui/             ← Shared composables: AttachmentChip, AttachmentList,
                       AttachmentPickerSheet, AttachmentViewerSheet
```

The `Attachment` proto message itself lives in `core/model` alongside the other proto messages (same as today — all protos compile there).

### `AttachmentManager` interface (`core/attachments/datamanager`)

```kotlin
interface AttachmentManager {
  /**
   * Upload a local file and return the resulting Attachment with storage_path
   * and download_url populated. Emits progress as a Flow<UploadProgress>.
   */
  suspend fun uploadFile(
    storagePath: String,
    localUri: String,
    mimeType: String,
    displayName: String,
  ): Result<Attachment>

  /**
   * Delete a file from Firebase Storage. No-op for LINK type attachments.
   */
  suspend fun deleteFile(attachment: Attachment): Result<Unit>

  /**
   * For cases where download_url is stale or missing — re-fetches from Firebase Storage.
   */
  suspend fun getDownloadUrl(storagePath: String): Result<String>
}
```

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

### Save sequence

```
User taps Save
  │
  ├─ 1. Upload all PendingAttachment.Local items in parallel
  │      AttachmentManager.uploadFile(storagePath, localUri, …)
  │      On failure: delete any files successfully uploaded in this batch → return error
  │
  ├─ 2. Delete all PendingAttachment.PendingDelete items
  │      AttachmentManager.deleteFile(attachment)
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
fun openAttachment(attachment: Attachment, context: PlatformContext) {
  when (attachment.type) {
    ATTACHMENT_TYPE_LINK  -> openUrl(attachment.url, context)
    ATTACHMENT_TYPE_IMAGE -> showInAppImageViewer(attachment.download_url)
    ATTACHMENT_TYPE_PDF,
    ATTACHMENT_TYPE_FILE  -> openWithSystemViewer(attachment.download_url, attachment.mime_type, context)
  }
}
```

For `openWithSystemViewer`:
- **Android**: `Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).setType(mimeType)` — passes the Firebase Storage download URL directly to the system. No local download required; Android can stream the file.
- **iOS**: `UIApplication.shared.open(URL(string: downloadUrl))` — same approach.

For `showInAppImageViewer`: a full-screen `ModalBottomSheet` (or dedicated screen) with a Coil `AsyncImage` loading from `download_url`. Coil's disk cache applies automatically.

If `download_url` is empty or stale (first open fails), the viewer calls `AttachmentManager.getDownloadUrl(storagePath)` as a fallback and updates the stored URL via a background write.

---

## Deletion of Parent Document

When a maintenance log or inspection card is deleted, its associated Firebase Storage files must also be deleted.

**Approach:** The existing `deleteLog` and `deleteInspection` methods are extended to:
1. Fetch the document's current `Attachment` list (already in memory in the ViewModel that triggered the delete, passed as a parameter).
2. Call `AttachmentManager.deleteFile(attachment)` for each non-link attachment.
3. Proceed with the Firestore document delete.

This is best-effort: if Storage deletion fails, the Firestore document is still deleted and orphaned Storage objects are acceptable (no data loss for the user; storage can be cleaned up via Firebase console or a future Cloud Function).

```kotlin
suspend fun deleteLog(aircraftId: String, log: MaintenanceLog): Result<Boolean> {
  log.attachments
    .filter { it.type != AttachmentType.ATTACHMENT_TYPE_LINK }
    .forEach { attachmentManager.deleteFile(it) }
  return deleteLogDocument(aircraftId, log.id)
}
```

---

## Dependency Graph (additions only)

```
core/model               ← Attachment proto message added here
core/attachments/datamanager  ← AttachmentManager, AttachmentStoragePath
  depends on: core/model, Firebase Storage, Koin
core/attachments/ui      ← AttachmentChip, AttachmentPickerSheet, AttachmentViewerSheet
  depends on: core/model, core/attachments/datamanager, core/ui, Compose, Coil

feature/maintenance      ← adds dep on core/attachments/ui
feature/inspection/update ← adds dep on core/attachments/ui
```

`feature/maintenance/database` and `feature/inspection/datamanager` gain the `AttachmentManager` as an injected dependency (for file deletion on parent delete).

---

## Open Questions / Decisions Needed

1. **Max attachment count per item.** Recommend 10 for V1. Beyond that the embedded proto grows large and the save-time upload becomes slow.
2. **Max file size.** Firebase Storage has no enforced limit, but large uploads on mobile are poor UX. Recommend a 25 MB soft limit enforced in the picker with a user-facing error.
3. **Image compression.** Should picked images be compressed before upload? Reduces cost and upload time at the expense of some quality. Recommend yes, compress to max 2048px and 85% JPEG quality for `ATTACHMENT_TYPE_IMAGE`.
4. **Anonymous users.** Anonymous users can currently create logs. Firebase Storage security rules must be decided: allow anonymous uploads (storage billed to the project) or require sign-in. Recommendation: require sign-in to upload files; show a prompt to sign in if the user tries to add an attachment while anonymous.
5. **Firebase Storage pricing.** Free tier: 5 GB storage, 1 GB/day download. For a personal logbook this is more than sufficient. Document in the release notes that attachments count against project storage quota.
