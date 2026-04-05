package dev.fanfly.wingslog.core.attachments.datamanager

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AttachmentOpenerAndroid(private val context: Context) : AttachmentOpener {

  override fun open(attachment: Attachment): Flow<OpenState> = flow {
    emit(OpenState.Downloading)
    try {
      when (attachment.type) {
        AttachmentType.ATTACHMENT_TYPE_LINK -> {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attachment.url))
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(intent)
          emit(OpenState.Done)
        }
        else -> {
          val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
          val request = DownloadManager.Request(Uri.parse(attachment.download_url))
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
    }
  }
}
