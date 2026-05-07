package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.coroutines.flow.Flow

/**
 * Local-first attachment coordinator. See docs/storage_r2_design.md §6.2.
 *
 * Replaces the R1 async-upload surface: in R2 the manager is a thin wrapper around
 * [LocalBlobStore] (and, in PR 5, an upload scheduler) — it does not block the form save flow
 * on a network upload. Adding a file is an ms-scale local write; uploads happen out-of-band and
 * surface through [observeStatus].
 */
interface AttachmentManager {

  /**
   * Copies [picked]'s bytes into the local blob store, computes sha256, and returns a populated
   * [Attachment] proto ready to be embedded in the owning entity. Anonymous users are no longer
   * blocked — bytes are local and will sync after the user signs in / links their account.
   *
   * @throws IllegalStateException if no Firebase user (anonymous or permanent) is signed in.
   */
  suspend fun addPickedFile(picked: PickedFile, displayName: String): Attachment

  /** Build a LINK [Attachment] with no blob. */
  fun makeLink(url: String, displayName: String): Attachment

  /**
   * Mark [attachment] for deletion. Tombstones the corresponding `blob_object` row so the
   * `BlobDeleteDriver` (PR 5) cleans up gs://. Removing the proto reference from the owning
   * entity is the caller's responsibility.
   */
  suspend fun delete(attachment: Attachment)

  /**
   * Trigger a foreground download of a REMOTE_ONLY attachment, verifying sha256 before writing
   * the local file. No-op if the blob is already SYNCED. The actual transfer is implemented in
   * PR 5; this PR ships the contract.
   */
  fun ensureLocal(attachment: Attachment): Flow<DownloadState>

  /** Reactive view of the upload pipeline's status for one attachment id. */
  fun observeStatus(attachmentId: String): Flow<AttachmentStatus>
}
