package dev.fanfly.wingslog.feature.attachment.datamanager

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.blobRelativePath
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * R2 [AttachmentOpener] for Android. Routes through [LocalBlobStore] instead of using the
 * proto's (now-reserved) `download_url` field. See docs/storage/storage_r2_design.md §7.
 */
class AttachmentOpenerAndroid(
  private val context: Context,
  private val blobs: LocalBlobStore,
  private val attachmentManager: AttachmentManager,
) : AttachmentOpener {

  private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
  override val downloadingIds: StateFlow<Set<String>> =
    _downloadingIds.asStateFlow()

  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    emit(OpenState.Downloading)

    if (attachment.type == AttachmentType.ATTACHMENT_TYPE_LINK) {
      val url = attachment.url.let {
        if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
      }
      context.startActivity(
        Intent(
          Intent.ACTION_VIEW,
          url.toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
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

  private suspend fun FlowCollector<OpenState>.emitOpenLocalFile(
    attachment: Attachment,
  ) {
    val blobFile = File(context.filesDir, blobRelativePath(attachment.id))
    if (!blobFile.exists()) {
      emit(OpenState.Failed(Exception("\"${attachment.displayName()}\" isn't on this device yet.")))
      return
    }
    try {
      // The on-disk blob is named by its internal id (`{id}.bin`), and FileProvider reports that as
      // the display name — so a viewer or "Save to Files" would keep it as `{id}.bin`. Materialize a
      // copy under the attachment's real display name (namespaced by id to avoid collisions between
      // two attachments that share a name) and hand THAT to the viewer.
      val namedFile = File(
        File(context.cacheDir, "attachment_open/${attachment.id}").apply { mkdirs() },
        attachment.displayName(),
      )
      if (!namedFile.exists() || namedFile.length() != blobFile.length()) {
        blobFile.copyTo(namedFile, overwrite = true)
      }
      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        namedFile,
      )
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, attachment.mime_type.ifEmpty { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(
        Intent.createChooser(intent, null)
          .also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          })
      emit(OpenState.Done)
    } catch (e: Exception) {
      emit(OpenState.Failed(e))
    }
  }
}

/**
 * The attachment's user-facing file name, sanitized for use as an actual filename: path separators
 * and characters that are illegal or reserved on common filesystems are replaced, and it falls back
 * to the id only when there is no usable name (never surfaced to the user in normal use).
 */
private fun Attachment.displayName(): String {
  val cleaned = name.trim().replace(Regex("""[/\\:*?"<>|\x00-\x1F]"""), "_")
  return cleaned.ifBlank { id }
}
