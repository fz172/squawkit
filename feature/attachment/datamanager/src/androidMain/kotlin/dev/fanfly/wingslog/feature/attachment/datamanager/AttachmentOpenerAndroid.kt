package dev.fanfly.wingslog.feature.attachment.datamanager

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AttachmentOpenerAndroid(private val context: Context) : AttachmentOpener {

  private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
  override val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    if (_downloadingIds.value.contains(attachment.id)) return@flow

    if (attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK) {
      _downloadingIds.update { it + attachment.id }
    }

    try {
      emit(OpenState.Downloading)
      when (attachment.type) {
        AttachmentType.ATTACHMENT_TYPE_LINK -> {
          val url = attachment.url.let {
            if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
          }
          val intent = Intent(Intent.ACTION_VIEW, url.toUri())
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(intent)
          emit(OpenState.Done)
        }

        else -> {
          val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
          val request = DownloadManager.Request(attachment.download_url.toUri())
            .setTitle(attachment.name)
            .setMimeType(attachment.mime_type.ifEmpty { "*/*" })
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.name)
          val downloadId = downloadManager.enqueue(request)

          // Poll for completion (max 5 min)
          val maxWaitMs = 5 * 60 * 1000L
          val startMs = System.currentTimeMillis()
          var done = false
          while (!done && System.currentTimeMillis() - startMs < maxWaitMs) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
              if (cursor.moveToFirst()) {
                val statusCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                when (cursor.getInt(statusCol)) {
                  DownloadManager.STATUS_SUCCESSFUL -> {
                    val fileUri = downloadManager.getUriForDownloadedFile(downloadId)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                      setDataAndType(fileUri, attachment.mime_type.ifEmpty { "*/*" })
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, null).also {
                      it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    done = true
                  }

                  DownloadManager.STATUS_FAILED -> {
                    val reasonCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    throw Exception("Download failed: reason=${cursor.getInt(reasonCol)}")
                  }
                }
              }
            }
            if (!done) delay(500)
          }
          if (!done) throw Exception("Download timed out")
          emit(OpenState.Done)
        }
      }
    } catch (e: Exception) {
      emit(OpenState.Failed(e))
    } finally {
      if (attachment.type != AttachmentType.ATTACHMENT_TYPE_LINK) {
        _downloadingIds.update { it - attachment.id }
      }
    }
  }
}
