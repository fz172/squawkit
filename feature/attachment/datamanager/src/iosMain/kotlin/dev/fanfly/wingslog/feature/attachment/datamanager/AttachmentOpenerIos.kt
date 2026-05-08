package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * R2 [AttachmentOpener] for iOS. Routes through [LocalBlobStore]; uses
 * `UIApplication.shared.openURL` for links and local file URLs.
 *
 * Note: opening local files via `openURL(file://)` hands the file to a compatible registered app
 * (e.g. Files). Full in-app QuickLook preview is deferred to M7.
 */
class AttachmentOpenerIos(
  private val blobs: LocalBlobStore,
  private val attachmentManager: AttachmentManager,
  private val fs: BlobFilesystem,
) : AttachmentOpener {

  private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
  override val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    emit(OpenState.Downloading)

    if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) {
      val urlString = attachment.url.let {
        if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
      }
      val url = NSURL.URLWithString(urlString) ?: throw Exception("Invalid URL: $urlString")
      UIApplication.sharedApplication.openURL(url)
      emit(OpenState.Done)
      return@flow
    }

    if (_downloadingIds.value.contains(attachment.id)) return@flow
    _downloadingIds.update { it + attachment.id }

    try {
      val ref = blobs.get(BlobId(attachment.id))
      when {
        ref == null && attachment.sha256.isBlank() -> emit(OpenState.Failed(LegacyAttachment()))

        ref == null || ref.remoteState == RemoteState.RemoteOnly -> {
          // Row missing (reconciler not yet run) or row present but not downloaded yet — same path.
          var downloadError: Throwable? = null
          attachmentManager.ensureLocal(attachment).collect { state ->
            if (state is DownloadState.Failed) downloadError = state.error
          }
          if (downloadError != null) {
            emit(OpenState.Failed(downloadError!!))
          } else {
            emitOpenLocalFile(attachment)
          }
        }

        else -> emitOpenLocalFile(attachment)
      }
    } catch (e: Exception) {
      emit(OpenState.Failed(e))
    } finally {
      _downloadingIds.update { it - attachment.id }
    }
  }

  private suspend fun kotlinx.coroutines.flow.FlowCollector<OpenState>.emitOpenLocalFile(
    attachment: Attachment,
  ) {
    val uriString = fs.uriFor(blobRelativePath(attachment.id))
    val url = NSURL.URLWithString(uriString) ?: NSURL.fileURLWithPath(uriString.removePrefix("file://"))
    UIApplication.sharedApplication.openURL(url)
    emit(OpenState.Done)
  }
}
