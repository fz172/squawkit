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

  override fun open(attachment: Attachment): Flow<OpenState> {
    // Browsers only honour window.open() during a user-gesture stack. Reserving the tab here —
    // while we're still in the onClick synchronous path — is what keeps the popup blocker quiet
    // for both links (we navigate directly) and blobs (we navigate later once OPFS bytes load).
    val isLink = attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK
    val initialUrl = if (isLink) attachment.url.normalizeWebUrl() else BLANK_URL
    val popup: dynamic = window.open(initialUrl, "_blank", "noopener,noreferrer")

    return flow {
      emit(OpenState.Downloading)

      if (popup == null) {
        emit(OpenState.Failed(IllegalStateException("Browser blocked opening attachment")))
        return@flow
      }

      if (isLink) {
        emit(OpenState.Done)
        return@flow
      }

      if (_downloadingIds.value.contains(attachment.id)) {
        // A concurrent open is already in flight — close this duplicate tab.
        closePopup(popup)
        return@flow
      }
      _downloadingIds.update { it + attachment.id }

      try {
        val ref = blobs.get(BlobId(attachment.id))
        when {
          ref == null && attachment.sha256.isBlank() -> {
            closePopup(popup)
            emit(OpenState.Failed(LegacyAttachment()))
          }

          ref == null || ref.remoteState == RemoteState.RemoteOnly -> {
            var downloadError: Throwable? = null
            attachmentManager.ensureLocal(attachment)
              .collect { state ->
                if (state is DownloadState.Failed) downloadError = state.error
              }
            if (downloadError != null) {
              closePopup(popup)
              emit(OpenState.Failed(downloadError))
            } else {
              navigatePopupToLocalBytes(popup, attachment)
              emit(OpenState.Done)
            }
          }

          else -> {
            navigatePopupToLocalBytes(popup, attachment)
            emit(OpenState.Done)
          }
        }
      } catch (e: Exception) {
        closePopup(popup)
        emit(OpenState.Failed(e))
      } finally {
        _downloadingIds.update { it - attachment.id }
      }
    }
  }

  private suspend fun navigatePopupToLocalBytes(popup: dynamic, attachment: Attachment) {
    val bytes = fs.read(blobRelativePath(attachment.id))
    val mimeType = attachment.mime_type.ifBlank { "application/octet-stream" }
    val objectUrl = createObjectUrl(bytes, mimeType)
    try {
      popup.location.href = objectUrl
      // Give the new tab time to load the bytes before revoking the URL.
      window.setTimeout(
        handler = { revokeObjectUrl(objectUrl) },
        timeout = OBJECT_URL_REVOKE_DELAY_MS,
      )
    } catch (e: Throwable) {
      revokeObjectUrl(objectUrl)
      closePopup(popup)
      throw e
    }
  }

  private fun closePopup(popup: dynamic) {
    try {
      popup.close()
    } catch (_: Throwable) {
      // Ignore — popup may already be closed or detached.
    }
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
    private const val BLANK_URL = "about:blank"
  }
}
