package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

private const val DISABLED_MESSAGE = "Attachments are not available on web"

internal class DisabledWebAttachmentManager : AttachmentManager {
  override suspend fun addPickedFile(
    aircraftId: String,
    picked: PickedFile,
    displayName: String,
  ): Attachment = error(DISABLED_MESSAGE)

  override fun makeLink(url: String, displayName: String): Attachment = error(DISABLED_MESSAGE)

  override suspend fun delete(attachment: Attachment) {
    error(DISABLED_MESSAGE)
  }

  override fun ensureLocal(attachment: Attachment): Flow<DownloadState> =
    flowOf(DownloadState.Failed(UnsupportedOperationException(DISABLED_MESSAGE)))

  override fun observeStatus(attachmentId: String): Flow<AttachmentStatus> =
    flowOf(AttachmentStatus.RemoteOnly)

  override fun observeBlobStates(scopePath: String): Flow<Map<String, BlobSyncState>> =
    flowOf(emptyMap())

  override suspend fun retryUpload(id: String) {
    error(DISABLED_MESSAGE)
  }

  override suspend fun wipeLocalData(uid: String) = Unit
}

internal class DisabledWebAttachmentOpener : AttachmentOpener {
  override val downloadingIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())

  override fun open(attachment: Attachment): Flow<OpenState> =
    flowOf(OpenState.Failed(UnsupportedOperationException(DISABLED_MESSAGE)))
}
