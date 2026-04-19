package dev.fanfly.wingslog.core.attachments.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentManager
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentStoragePath
import dev.fanfly.wingslog.core.attachments.datamanager.FileByteReader
import dev.fanfly.wingslog.core.attachments.datamanager.UploadState
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AttachmentManagerImpl(
  private val storage: FirebaseStorage,
  private val auth: FirebaseAuth,
  private val fileByteReader: FileByteReader,
) : AttachmentManager {

  override fun buildMaintenanceLogPath(
    aircraftId: String,
    logId: String,
    attachmentId: String,
    filename: String,
  ): String? {
    val uid = authedUid() ?: return null
    return AttachmentStoragePath.forMaintenanceLog(uid, aircraftId, logId, attachmentId, filename)
  }

  override fun buildMaintenanceTaskPath(
    aircraftId: String,
    cardId: String,
    attachmentId: String,
    filename: String,
  ): String? {
    val uid = authedUid() ?: return null
    return AttachmentStoragePath.forMaintenanceTask(uid, aircraftId, cardId, attachmentId, filename)
  }

  override fun uploadFile(
    storagePath: String,
    localUri: String,
    mimeType: String,
    displayName: String,
    attachmentId: String,
  ): Flow<UploadState> = flow {
    emit(UploadState.Uploading(0f))
    try {
      val bytes: ByteArray =
        fileByteReader.readBytes(localUri) ?: throw Exception("Could not read file at: $localUri")

      val ref = storage.reference.child(storagePath)
      ref.putData(bytes.toFirebaseData())

      val downloadUrl = ref.getDownloadUrl()

      val attachment = Attachment(
        id = attachmentId,
        name = displayName,
        type = mimeType.toAttachmentType(),
        storage_path = storagePath,
        download_url = downloadUrl,
        mime_type = mimeType,
        size_bytes = bytes.size.toLong(),
      )
      emit(UploadState.Done(attachment))
    } catch (e: Exception) {
      logger.w(e) { "Upload failed for $storagePath" }
      emit(UploadState.Failed(e))
    }
  }

  override suspend fun deleteFile(attachment: Attachment): Result<Unit> {
    if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) return Result.success(Unit)
    if (attachment.storage_path.isBlank()) return Result.success(Unit)
    return try {
      storage.reference.child(attachment.storage_path).delete()
      Result.success(Unit)
    } catch (e: Exception) {
      logger.w(e) { "Failed to delete ${attachment.storage_path}" }
      Result.failure(e)
    }
  }

  override suspend fun getDownloadUrl(storagePath: String): Result<String> = try {
    Result.success(storage.reference.child(storagePath).getDownloadUrl())
  } catch (e: Exception) {
    Result.failure(e)
  }

  private fun authedUid(): String? {
    val user = auth.currentUser ?: return null
    if (user.isAnonymous) return null
    return user.uid
  }

  private fun String.toAttachmentType(): AttachmentType = when {
    startsWith("image/") -> AttachmentType.ATTACHMENT_TYPE_IMAGE
    this == "application/pdf" -> AttachmentType.ATTACHMENT_TYPE_PDF
    else -> AttachmentType.ATTACHMENT_TYPE_FILE
  }

  companion object {
    private val logger = Logger.withTag("AttachmentManagerImpl")
  }
}
