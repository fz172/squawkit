package dev.fanfly.wingslog.feature.attachment.model

import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType

/**
 * Form-side attachment state. R2 dropped the R1 [Uploading] / [Failed] variants — uploads no
 * longer block save, and per-attachment status is exposed reactively via
 * `AttachmentManager.observeStatus` instead.
 *
 * - [Local]         — newly added by `addPickedFile`; already on disk, will be uploaded out-of-band.
 * - [LocalLink]     — hyperlink added this session.
 * - [LocalDataLogRef] — reference to a DataLog record added this session; owns no blob.
 * - [Saved]         — already in the parent proto (includes previously-saved links).
 * - [PendingDelete] — a [Saved] file marked for tombstone on save.
 */
sealed class PendingAttachment {
  abstract val attachment: Attachment
  val id: String get() = attachment.id
  val name: String get() = attachment.name

  /** A locally-stored attachment whose proto is fully populated (sha256 included). */
  data class Local(override val attachment: Attachment) : PendingAttachment()

  data class LocalLink(override val attachment: Attachment) :
    PendingAttachment()

  data class LocalDataLogRef(override val attachment: Attachment) :
    PendingAttachment()

  data class Saved(override val attachment: Attachment) : PendingAttachment()

  /** Shown as removed in the list; tombstoned on save. */
  data class PendingDelete(override val attachment: Attachment) :
    PendingAttachment()
}

/** The data logs referenced from this parent, pending deletes excluded. */
fun List<PendingAttachment>.dataLogIds(): Set<DataLogId> =
  filter { it !is PendingAttachment.PendingDelete }.mapNotNull { it.attachment.data_log_id }
    .toSet()

/**
 * Counts file attachments (not links, not data log references, not pending-delete) — enforces the
 * per-parent file cap.
 */
fun List<PendingAttachment>.fileCount(): Int = count { pending ->
  when (pending) {
    is PendingAttachment.Local -> true
    is PendingAttachment.Saved -> pending.attachment.type.isFile
    else -> false
  }
}

/** True for the types that carry a blob; LINK and DATA_LOG are references and own nothing. */
val AttachmentType.isFile: Boolean
  get() = this != AttachmentType.ATTACHMENT_TYPE_LINK && this != AttachmentType.ATTACHMENT_TYPE_DATA_LOG

/** Visible items (excludes [PendingAttachment.PendingDelete]) for rendering. */
fun List<PendingAttachment>.visible(): List<PendingAttachment> =
  filter { it !is PendingAttachment.PendingDelete }
