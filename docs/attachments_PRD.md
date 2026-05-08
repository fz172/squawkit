# PRD: Attachments for Maintenance Logs and Inspection Items

## Overview

Users need to attach supporting documents and media to maintenance log entries and inspection cards — service letters, AD notices, photos of work performed, 8130 forms, part condition photos, manufacturer manuals, and external references. Today there is no way to link these to a specific log or inspection item, so records are incomplete and users have to manage files externally.

---

## Goals

- Allow users to attach one or more files or links to any maintenance log entry.
- Allow users to attach one or more files or links to any inspection card.
- Attachments are accessible when viewing the log or inspection item.
- Supported attachment types: images (JPG, HEIC, PNG), PDFs, plain text files, any other file the phone can open, and hyperlinks.
- Files are stored in the cloud and accessible across devices.

## Non-Goals (V1)

- Offline access to attachment content (metadata is available offline; file content requires network).
- Bulk attachment management or a standalone "documents" section.
- Attachment search.
- Attachment size limits enforced in UI (Firebase Storage quotas apply at the platform level).
- Video files (added in a future release once storage cost implications are understood).

---

## User Stories

**Maintenance log — add/edit**
- As a mechanic, when creating or editing a maintenance log, I can attach one or more photos of the work performed so there is a visual record linked to the entry.
- As a mechanic, I can attach the PDF service bulletin or AD that the work complies with so traceability is complete.
- As a mechanic, I can paste a hyperlink (e.g. FAA AD database URL) as an attachment when I do not have the document locally.
- As a mechanic, I can remove an attachment I added before saving.

**Inspection card — add/edit**
- As an owner, when creating or editing an inspection card for an AD or SB, I can attach the relevant document so the card is self-contained.
- As an owner, I can attach a hyperlink to the manufacturer's SB page.

**Viewing**
- As a user, when I tap an image attachment, it opens full-screen inside the app.
- As a user, when I tap a PDF or other file attachment, it opens using the phone's default viewer (Files app, PDF reader, etc.).
- As a user, when I tap a hyperlink attachment, it opens in the default browser.
- As a user, attachments are listed on the log detail view and on the inspection detail sheet.

---

## Requirements

### Functional

| ID | Requirement |
|----|-------------|
| F1 | Add/remove attachments during maintenance log create and edit flows. |
| F2 | Add/remove attachments during inspection card create and edit flows. |
| F3 | Display all attachments on the maintenance log detail view. |
| F4 | Display all attachments on the inspection detail sheet. |
| F5 | Support image, PDF, plain text, generic file, and hyperlink attachment types. |
| F6 | In-app full-screen viewer for image attachments. |
| F7 | System-delegate opening for PDF, text, and generic file attachments. |
| F8 | System browser opening for hyperlink attachments. |
| F9 | Attachments persist across devices (cloud-backed). |
| F10 | Deleting a maintenance log or inspection card also deletes its uploaded files from storage. |
| F11 | Each attachment has a user-visible display name that can be customised (defaults to filename or domain for links). |
| F12 | Per-parent size cap: the sum of file attachment sizes on any one log or inspection card must not exceed **25 MB**. The picker enforces this before adding a file. |
| F13 | Per-user storage cap: the sum of all of a user's file attachments across every log and inspection card must not exceed **1 GB**. The picker enforces this before adding a file. |
| F14 | Per-parent duplicate prevention: the picker rejects a file whose content matches another file already attached (or pending) on the same log/card. Identity is by sha256 of the bytes; filename and display name don't count. |

### Non-Functional

| ID | Requirement |
|----|-------------|
| N1 | (R1) Upload happens at save time; the form does not block on upload until the user taps Save. **(R2 supersedes — saves write bytes locally, uploads run in the background; the form returns immediately.)** |
| N2 | (R1) Each save shows a progress indicator while uploads are in flight. **(R2 supersedes — per-attachment status badge on the row instead of a save-level spinner.)** |
| N3 | If an upload fails mid-save, already-uploaded files for that save attempt are cleaned up (best-effort). |
| N4 | Attachment metadata is included in the same Firestore write as the parent document (atomic with respect to metadata). |
| N5 | No change to existing log or inspection data that has no attachments. |
| N6 | (R2) Quotas are enforced client-side in the picker. Server-side enforcement is best-effort — Firebase Storage rules should reject obviously-oversized objects (>25 MB single put), but per-user 1 GB is a client-side soft cap. |

---

## UX Flows

### Add/edit form — attachment section

Located at the bottom of the relevant form (below existing fields):

1. **Attachment row list** — each pending or saved attachment shown as a chip or row: icon (type), name, × to remove.
2. **"Add attachment" button** — opens a bottom sheet with two options:
   - **Choose file** → native OS file/photo picker (supports images, PDFs, any document).
   - **Add link** → inline text field to paste or type a URL, with a "Done" confirmation.
3. Attachments added during a session are held in ViewModel state as "pending" until Save is tapped.
4. Tapping × on a pending attachment removes it from the pending list (no network call yet).
5. Tapping × on an already-saved attachment marks it for deletion; deletion executes on Save.

### Viewing

- Attachments section appears on the log detail view and the inspection detail sheet below existing content.
- Each attachment is a tappable row: type icon | name | size or domain | **sync-state icon**.
- Tap behaviour is type-driven (see F6–F8).
- If there are no attachments, the section is hidden.

#### Sync-state status icon (R2 / M5)

Every file attachment row (not links) shows a trailing status icon. The icon reflects the blob's current `RemoteState` plus in-flight activity from the opener/uploader:

| State | Icon | Tint | Row tappable? |
|---|---|---|---|
| `PendingUpload` — local, never attempted | `CloudUpload` (outlined) | `onSurfaceVariant` | No — not yet openable |
| `Uploading` — upload in flight | `CircularProgressIndicator` 16 dp / 2 dp stroke | `primary` | No — dimmed |
| `Synced` — local + remote copy exist | `CloudDone` (outlined) | `onSurfaceVariant` | Yes |
| `RemoteOnly` — remote copy only, no local file | `CloudDownload` (outlined) | `onSurfaceVariant` | Yes — download on tap |
| `Downloading` — download in flight | `CircularProgressIndicator` 16 dp / 2 dp stroke | `onSurfaceVariant` | No — dimmed |
| `UploadFailed` — `LOCAL_ONLY` after ≥ 1 failed attempt | `SyncProblem` (outlined) | `error` | Tap → retry prompt |

`UploadFailed` vs `PendingUpload` is determined by `blob_object.upload_attempts > 0`; no new `RemoteState` value is needed.

Tapping an `UploadFailed` row shows a prompt: **"Upload failed — This file is still saved on this device. [Retry] [Dismiss]"**. Retry resets `upload_attempts = 0` so the uploader picks the row up on its next pass.
