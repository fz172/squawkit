package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker.Companion.MAX_FILE_ATTACHMENTS
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker.Companion.MAX_FILE_SIZE_BYTES
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.model.fileCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared form-side attachment state machine used by the task, squawk, and maintenance-log
 * edit forms. Owns the [PendingAttachment] list and the add/remove/resolve lifecycle so each
 * ViewModel doesn't re-implement it.
 *
 * Deliberately UI-free: errors surface through [AddFileError] and the owning ViewModel maps
 * them to its own user-facing strings. Suspend functions run in the caller's scope (typically
 * `viewModelScope`).
 */
class AttachmentFormController(
  private val attachmentManager: AttachmentManager,
  private val aircraftId: String,
) {

  /** Why a picked file could not be added. The owning ViewModel maps these to UiText. */
  sealed interface AddFileError {
    /** File exceeds [MAX_FILE_SIZE_BYTES]; the file is skipped and iteration continues. */
    data object FileTooLarge : AddFileError

    /** Copying the file into local blob storage failed. */
    data class Failed(val message: String?) : AddFileError
  }

  private val _pendingAttachments =
    MutableStateFlow<List<PendingAttachment>>(emptyList())
  val pendingAttachments: StateFlow<List<PendingAttachment>> =
    _pendingAttachments.asStateFlow()

  private val _showPicker = MutableStateFlow(false)
  val showPicker: StateFlow<Boolean> = _showPicker.asStateFlow()

  val filesAtLimit: Boolean
    get() = _pendingAttachments.value.fileCount() >= MAX_FILE_ATTACHMENTS

  fun showPicker() {
    _showPicker.value = true
  }

  fun hidePicker() {
    _showPicker.value = false
  }

  /**
   * Seeds the list with the parent entity's saved attachments. No-op if anything is already
   * present, so re-emissions of the parent while editing don't clobber in-flight changes.
   */
  fun seedIfEmpty(attachments: List<Attachment>) {
    if (_pendingAttachments.value.isNotEmpty()) return
    _pendingAttachments.value = attachments.map { PendingAttachment.Saved(it) }
  }

  /**
   * Copies picked files into local blob storage and appends them as [PendingAttachment.Local].
   * Stops once the file cap is reached; oversized files are skipped with
   * [AddFileError.FileTooLarge]. Returns true if at least one file was added.
   */
  suspend fun addLocalFiles(
    files: List<PickedFile>,
    onError: (AddFileError) -> Unit,
  ): Boolean {
    var anyAdded = false
    for (file in files) {
      if (_pendingAttachments.value.fileCount() >= MAX_FILE_ATTACHMENTS) break
      // Photos are size-checked *after* compression, inside addPickedFile, so a large image can
      // be rescued by shrinking under the cap. Non-photos are rejected up front so we never read
      // a huge file fully into memory just to fail it.
      if (!isCompressiblePhotoMime(file.mimeType) && file.sizeBytes > MAX_FILE_SIZE_BYTES) {
        onError(AddFileError.FileTooLarge)
        continue
      }
      try {
        val attachment = attachmentManager.addPickedFile(
          aircraftId,
          file,
          file.name
        )
        _pendingAttachments.update { it + PendingAttachment.Local(attachment) }
        anyAdded = true
      } catch (e: CancellationException) {
        throw e
      } catch (e: FileTooLargeException) {
        onError(AddFileError.FileTooLarge)
      } catch (e: Exception) {
        onError(AddFileError.Failed(e.message))
      }
    }
    return anyAdded
  }

  fun addLink(
    url: String,
    name: String,
  ) {
    val displayName = name.ifBlank { url.take(40) }
    val attachment = attachmentManager.makeLink(
      url,
      displayName
    )
    _pendingAttachments.update { it + PendingAttachment.LocalLink(attachment) }
  }

  /**
   * Removes the attachment with [id]: session-local items and saved links disappear outright;
   * saved files become [PendingAttachment.PendingDelete] and are tombstoned on save.
   */
  fun remove(id: String) {
    _pendingAttachments.update { list ->
      list.mapNotNull { pending ->
        when {
          pending.id != id -> pending
          pending is PendingAttachment.Local -> null
          pending is PendingAttachment.LocalLink -> null
          pending is PendingAttachment.Saved && pending.attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK -> null
          pending is PendingAttachment.Saved -> PendingAttachment.PendingDelete(
            pending.attachment
          )

          else -> pending
        }
      }
    }
  }

  /**
   * Tombstones pending-delete attachments (best-effort; BlobDeleteDriver finishes cleanup) and
   * returns the final attachment list for the parent proto. Local items already have fully
   * populated protos — addPickedFile ran at pick time, so there is no network wait here.
   */
  suspend fun resolveForSave(): List<Attachment> {
    val pending = _pendingAttachments.value
    pending.filterIsInstance<PendingAttachment.PendingDelete>()
      .forEach { attachmentManager.delete(it.attachment) }
    return buildList {
      addAll(
        pending.filterIsInstance<PendingAttachment.Saved>()
          .map { it.attachment })
      addAll(
        pending.filterIsInstance<PendingAttachment.Local>()
          .map { it.attachment })
      addAll(
        pending.filterIsInstance<PendingAttachment.LocalLink>()
          .map { it.attachment })
    }
  }

  /**
   * Tombstones every saved file attachment (links excluded) — called when the parent entity
   * itself is deleted.
   */
  suspend fun deleteSavedFiles() {
    _pendingAttachments.value
      .filterIsInstance<PendingAttachment.Saved>()
      .filter { it.attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK }
      .forEach { attachmentManager.delete(it.attachment) }
  }
}
