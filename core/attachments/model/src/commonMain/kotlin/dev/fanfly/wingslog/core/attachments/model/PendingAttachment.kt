package dev.fanfly.wingslog.core.attachments.model

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType

/**
 * Represents an attachment in the form's pending state before the parent document is saved.
 *
 * - [LocalFile]      — picked from device, not yet uploaded.
 * - [LocalLink]      — hyperlink added this session, not yet persisted.
 * - [Saved]          — already exists in Firestore (includes previously-saved links).
 * - [PendingDelete]  — a Saved file attachment marked for Storage + proto removal on save.
 */
sealed class PendingAttachment {
  abstract val id: String
  abstract val name: String

  data class LocalFile(
    val tempId: String,
    override val name: String,
    val localUri: String,
    val mimeType: String,
    val sizeBytes: Long,
  ) : PendingAttachment() {
    override val id: String get() = tempId
  }

  /**
   * A [LocalFile] that is currently being uploaded to Firebase Storage.
   * [progress] is 0–1; 0f means indeterminate.
   */
  data class Uploading(
    val tempId: String,
    override val name: String,
    val progress: Float = 0f,
  ) : PendingAttachment() {
    override val id: String get() = tempId
  }

  data class LocalLink(
    val tempId: String,
    override val name: String,
    val url: String,
  ) : PendingAttachment() {
    override val id: String get() = tempId
  }

  data class Saved(val attachment: Attachment) : PendingAttachment() {
    override val id: String get() = attachment.id
    override val name: String get() = attachment.name
  }

  /** Shown in the list as removed; will be deleted from Firebase Storage on save. */
  data class PendingDelete(val attachment: Attachment) : PendingAttachment() {
    override val id: String get() = attachment.id
    override val name: String get() = attachment.name
  }
}

/** Counts file attachments (not links, not pending-delete). Used to enforce the 3-file cap. */
fun List<PendingAttachment>.fileCount(): Int = count { pending ->
  when (pending) {
    is PendingAttachment.LocalFile -> true
    is PendingAttachment.Uploading -> true
    is PendingAttachment.Saved -> pending.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK
    else -> false
  }
}

/** Visible items to show in the UI (excludes PendingDelete). */
fun List<PendingAttachment>.visible(): List<PendingAttachment> =
  filter { it !is PendingAttachment.PendingDelete }
