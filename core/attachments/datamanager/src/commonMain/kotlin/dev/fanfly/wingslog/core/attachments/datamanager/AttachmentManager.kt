package dev.fanfly.wingslog.core.attachments.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import kotlinx.coroutines.flow.Flow

interface AttachmentManager {

  /**
   * Builds the Firebase Storage path for a maintenance log attachment.
   * Returns null if no authenticated non-anonymous user is signed in.
   */
  fun buildMaintenanceLogPath(
    aircraftId: String,
    logId: String,
    attachmentId: String,
    filename: String,
  ): String?

  /**
   * Builds the Firebase Storage path for an inspection card attachment.
   * Returns null if no authenticated non-anonymous user is signed in.
   */
  fun buildInspectionCardPath(
    aircraftId: String,
    cardId: String,
    attachmentId: String,
    filename: String,
  ): String?

  /**
   * Uploads a local file to Firebase Storage and emits [UploadState].
   * Terminal emission is [UploadState.Done] (with the fully-populated [Attachment]) or
   * [UploadState.Failed].
   */
  fun uploadFile(
    storagePath: String,
    localUri: String,
    mimeType: String,
    displayName: String,
    attachmentId: String,
  ): Flow<UploadState>

  /**
   * Deletes a file from Firebase Storage. No-op for [dev.fanfly.wingslog.aircraft.AttachmentType.ATTACHMENT_TYPE_LINK].
   */
  suspend fun deleteFile(attachment: Attachment): Result<Unit>

  /**
   * Re-fetches the download URL from Firebase Storage (use when the stored URL is stale).
   */
  suspend fun getDownloadUrl(storagePath: String): Result<String>
}
