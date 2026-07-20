package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.blobRelativePath
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

// Retained for the life of a preview — UIDocumentInteractionController.delegate is weak, so nothing
// else holds these once emitOpenLocalFile returns.
private var activeDocController: UIDocumentInteractionController? = null
private var activeDocDelegate: NSObject? = null

/**
 * R2 [AttachmentOpener] for iOS. Routes through [LocalBlobStore]; opens links with
 * `UIApplication.openURL` and local files with a QuickLook preview
 * ([UIDocumentInteractionController.presentPreviewAnimated]), falling back to a share sheet for
 * types QuickLook can't render.
 */
class AttachmentOpenerIos(
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
      val urlString = attachment.url.let {
        if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
      }
      val url = NSURL.URLWithString(urlString)
        ?: throw Exception("Invalid URL: $urlString")
      UIApplication.sharedApplication.openURL(url)
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
          // Row missing (reconciler not yet run) or row present but not downloaded yet — same path.
          var downloadError: Throwable? = null
          attachmentManager.ensureLocal(attachment)
            .collect { state ->
              if (state is DownloadState.Failed) downloadError = state.error
            }
          if (downloadError != null) {
            emit(OpenState.Failed(downloadError))
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

  @OptIn(ExperimentalForeignApi::class)
  private suspend fun kotlinx.coroutines.flow.FlowCollector<OpenState>.emitOpenLocalFile(
    attachment: Attachment,
  ) {
    val blobUri = fs.uriFor(blobRelativePath(attachment.id))
    val blobPath = NSURL.URLWithString(blobUri)?.path
      ?: blobUri.removePrefix("file://")

    // The on-disk blob is named by its internal id (`{id}.bin`), so Files / the system viewer would
    // keep that name. Copy it to a temp file under the attachment's real display name (namespaced by
    // id to avoid collisions between two attachments that share a name) and open THAT, so the file
    // keeps its name (e.g. error_57.txt). The OS reclaims NSTemporaryDirectory on its own.
    val fm = NSFileManager.defaultManager
    val dir = NSTemporaryDirectory() + "attachment_open/" + attachment.id
    fm.createDirectoryAtPath(dir, true, null, null)
    val namedPath = "$dir/${attachment.displayFileName()}"
    fm.removeItemAtPath(namedPath, null)
    val copied = fm.copyItemAtPath(blobPath, toPath = namedPath, error = null)
    if (!copied) {
      emit(OpenState.Failed(Exception("\"${attachment.displayFileName()}\" isn't on this device yet.")))
      return
    }

    val opened = withContext(Dispatchers.Main) {
      presentPreview(NSURL.fileURLWithPath(namedPath))
    }
    if (opened) {
      emit(OpenState.Done)
    } else {
      emit(OpenState.Failed(Exception("Couldn't open \"${attachment.displayFileName()}\".")))
    }
  }

  /**
   * Preview a local file via QuickLook. `openURL(file://)` does NOT open local files (it's for URL
   * schemes), which is why taps silently did nothing. [UIDocumentInteractionController] previews the
   * common types (images, PDF, docs) in-app; anything QuickLook can't render falls back to a share
   * sheet ("Open in…" / Save). Must run on the main thread. Returns false only if there's no window.
   */
  private fun presentPreview(url: NSURL): Boolean {
    val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
      ?.topMostPresented() ?: return false
    val delegate = DocInteractionDelegate(presenter)
    val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
    controller.delegate = delegate
    activeDocController = controller
    activeDocDelegate = delegate

    if (controller.presentPreviewAnimated(true)) return true

    // No QuickLook preview for this type — offer the share sheet instead.
    val activity = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    // iPad requires a popover anchor or it throws.
    activity.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(activity, animated = true, completion = null)
    return true
  }
}

private fun UIViewController.topMostPresented(): UIViewController {
  var vc = this
  while (true) vc = vc.presentedViewController ?: return vc
}

private class DocInteractionDelegate(
  private val presenter: UIViewController,
) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
  override fun documentInteractionControllerViewControllerForPreview(
    controller: UIDocumentInteractionController,
  ): UIViewController = presenter

  override fun documentInteractionControllerDidEndPreview(
    controller: UIDocumentInteractionController,
  ) {
    activeDocController = null
    activeDocDelegate = null
  }
}

/**
 * The attachment's user-facing file name, sanitized for use as an actual filename: path separators
 * and characters that are illegal or reserved on common filesystems are replaced, falling back to
 * the id only when there is no usable name (never surfaced to the user in normal use).
 */
private fun Attachment.displayFileName(): String {
  val cleaned = name.trim()
    .map { c -> if (c == '/' || c == '\\' || c.code < 0x20 || c in ":*?\"<>|") '_' else c }
    .joinToString("")
  return cleaned.ifBlank { id }
}
