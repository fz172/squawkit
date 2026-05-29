package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.khronos.webgl.Uint8Array

internal class AttachmentOpenerWeb(
  private val blobs: LocalBlobStore,
  private val attachmentManager: AttachmentManager,
  private val fs: BlobFilesystem,
) : AttachmentOpener {
  private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
  override val downloadingIds: StateFlow<Set<String>> =
    _downloadingIds.asStateFlow()

  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    emit(OpenState.Downloading)

    if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) {
      openUrl(attachment.url.normalizeWebUrl())
      emit(OpenState.Done)
      return@flow
    }

    if (_downloadingIds.value.contains(attachment.id)) return@flow
    _downloadingIds.update { it + attachment.id }

    try {
      val ref = blobs.get(BlobId(attachment.id))
      when {
        ref == null && attachment.sha256.isBlank() -> emit(
          OpenState.Failed(
            LegacyAttachment()
          )
        )

        ref == null || ref.remoteState == RemoteState.RemoteOnly -> {
          var downloadError: Throwable? = null
          attachmentManager.ensureLocal(attachment)
            .collect { state ->
              if (state is DownloadState.Failed) downloadError = state.error
            }
          if (downloadError != null) {
            emit(OpenState.Failed(downloadError))
          } else {
            openLocalBytes(attachment)
            emit(OpenState.Done)
          }
        }

        else -> {
          openLocalBytes(attachment)
          emit(OpenState.Done)
        }
      }
    } catch (e: Exception) {
      emit(OpenState.Failed(e))
    } finally {
      _downloadingIds.update { it - attachment.id }
    }
  }

  private suspend fun openLocalBytes(attachment: Attachment) {
    val bytes = fs.read(blobRelativePath(attachment.id))
    val mimeType = attachment.mime_type.ifBlank { "application/octet-stream" }
    val objectUrl = createObjectUrl(bytes, mimeType)
    try {
      openUrl(objectUrl)
      window.setTimeout(
        handler = { revokeObjectUrl(objectUrl) },
        timeout = OBJECT_URL_REVOKE_DELAY_MS,
      )
    } catch (e: Throwable) {
      revokeObjectUrl(objectUrl)
      throw e
    }
  }

  private fun openUrl(url: String) {
    window.open(url, "_blank", "noopener,noreferrer")
      ?: throw IllegalStateException("Browser blocked opening attachment")
  }

  private fun createObjectUrl(bytes: ByteArray, mimeType: String): String {
    val data = Uint8Array(bytes.toTypedArray())
    return js(
      "URL.createObjectURL(new Blob([data], { type: mimeType }))"
    ).unsafeCast<String>()
  }

  private fun revokeObjectUrl(url: String) {
    js("URL.revokeObjectURL(url)")
  }

  private fun String.normalizeWebUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this else "https://$this"

  private companion object {
    private const val OBJECT_URL_REVOKE_DELAY_MS = 60_000
  }
}
