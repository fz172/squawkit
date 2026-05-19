package dev.fanfly.wingslog.feature.export.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberExportFileOpener(): ExportFileOpener {
  val context = LocalContext.current
  return remember(context) {
    AndroidExportFileOpener(context.applicationContext)
  }
}

private class AndroidExportFileOpener(
  private val context: Context,
) : ExportFileOpener {

  override fun open(filePath: String): Boolean {
    val file = File(filePath)
    if (!file.exists()) return false

    val contentUri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(contentUri, "application/zip")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, null).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(chooser)
    }.isSuccess
  }
}
