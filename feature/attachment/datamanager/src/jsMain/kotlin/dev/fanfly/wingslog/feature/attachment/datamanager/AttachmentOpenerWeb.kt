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
    // Browsers only honour window.open() during a user-gesture stack. Both paths below must run
    // synchronously inside the click callback.
    //
    // Links: dispatch via a programmatic <a target="_blank" rel="noopener noreferrer"> click.
    // Anchor clicks during a gesture are never popup-blocked, and we get noopener/noreferrer
    // without window.open's lie-about-null-on-success behaviour.
    //
    // Blobs: window.open(about:blank) gives us a real popup handle (no noopener), which we
    // navigate to the object URL once OPFS bytes load. Null out popup.opener immediately to
    // defuse reverse-tabnabbing on the preview tab.
    val isLink = attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK
    if (isLink) {
      clickExternalLink(attachment.url.normalizeWebUrl())
      return flow { emit(OpenState.Done) }
    }

    val popup: dynamic = window.open(BLANK_URL, "_blank")
    if (popup != null) {
      try { popup.opener = null } catch (_: Throwable) { /* may throw in some sandboxes */ }
    }

    return flow {
      emit(OpenState.Downloading)

      if (popup == null) {
        emit(OpenState.Failed(IllegalStateException("Browser blocked opening attachment")))
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

  private fun clickExternalLink(url: String) {
    val anchor = window.document.createElement("a").asDynamic()
    anchor.href = url
    anchor.target = "_blank"
    anchor.rel = "noopener noreferrer"
    // Append → click → remove. Not strictly required by spec but works across all browsers.
    window.document.body?.appendChild(anchor.unsafeCast<org.w3c.dom.Node>())
    anchor.click()
    window.document.body?.removeChild(anchor.unsafeCast<org.w3c.dom.Node>())
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
