package dev.fanfly.wingslog.feature.attachment.model

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType

/**
 * Form-side attachment state. R2 dropped the R1 [Uploading] / [Failed] variants — uploads no
 * longer block save, and per-attachment status is exposed reactively via
 * `AttachmentManager.observeStatus` instead.
 *
 * - [Local]         — newly added by `addPickedFile`; already on disk, will be uploaded out-of-band.
 * - [LocalLink]     — hyperlink added this session.
 * - [Saved]         — already in the parent proto (includes previously-saved links).
 * - [PendingDelete] — a [Saved] file marked for tombstone on save.
 */
sealed class PendingAttachment {
  abstract val id: String
  abstract val name: String

  /** A locally-stored attachment whose proto is fully populated (sha256 included). */
  data class Local(val attachment: Attachment) : PendingAttachment() {
    override val id: String get() = attachment.id
    override val name: String get() = attachment.name
  }

  data class LocalLink(val attachment: Attachment) : PendingAttachment() {
    override val id: String get() = attachment.id
    override val name: String get() = attachment.name
  }

  data class Saved(val attachment: Attachment) : PendingAttachment() {
    override val id: String get() = attachment.id
    override val name: String get() = attachment.name
  }

  /** Shown as removed in the list; tombstoned on save. */
  data class PendingDelete(val attachment: Attachment) : PendingAttachment() {
    override val id: String get() = attachment.id
    override val name: String get() = attachment.name
  }
}

/** Counts file attachments (not links, not pending-delete) — enforces the per-parent file cap. */
fun List<PendingAttachment>.fileCount(): Int = count { pending ->
  when (pending) {
    is PendingAttachment.Local -> true
    is PendingAttachment.Saved -> pending.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK
    else -> false
  }
}

/** Visible items (excludes [PendingAttachment.PendingDelete]) for rendering. */
fun List<PendingAttachment>.visible(): List<PendingAttachment> =
  filter { it !is PendingAttachment.PendingDelete }
