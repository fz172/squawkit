package dev.fanfly.wingslog.feature.export.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberExportFileSharer(): ExportFileSharer {
  val context = LocalContext.current
  return remember(context) {
    AndroidExportFileSharer(context.applicationContext)
  }
}

private class AndroidExportFileSharer(
  private val context: Context,
) : ExportFileSharer {

  override fun share(filePath: String, chooserTitle: String, subject: String, body: String): Boolean {
    val file = File(filePath)
    if (!file.exists()) return false

    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "application/zip"
      putExtra(Intent.EXTRA_STREAM, contentUri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, body)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      selector = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
      }
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(chooser)
    }.isSuccess
  }
}
