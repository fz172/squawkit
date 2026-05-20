package dev.fanfly.wingslog.feature.export.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

private const val TAG = "ExportFileSharer"

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

  override fun share(
    filePath: String,
    chooserTitle: String,
    subject: String,
    body: String,
  ): Boolean {
    // filePath is a MediaStore content:// URI produced by ExportFileStore.
    val streamUri = filePath.toUri()
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/zip"
      putExtra(Intent.EXTRA_STREAM, streamUri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, body)
      clipData = ClipData.newUri(context.contentResolver, subject, streamUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Resolve installed email apps directly. Relying on a `mailto:` selector is unreliable
    // across devices, so we target the email packages explicitly and only fall back to the
    // generic chooser when no email app is available.
    val emailApps = context.packageManager.queryIntentActivities(
      Intent(Intent.ACTION_SENDTO, "mailto:".toUri()),
      PackageManager.ResolveInfoFlags.of(0L),
    )
    Log.d(TAG, "Resolved ${emailApps.size} email app(s) for export share")

    val launchIntent = when {
      emailApps.isEmpty() -> Intent.createChooser(baseIntent, chooserTitle)
      emailApps.size == 1 ->
        Intent(baseIntent).setPackage(emailApps.first().activityInfo.packageName)
      else -> {
        val targeted = emailApps.map { resolveInfo ->
          Intent(baseIntent).setPackage(resolveInfo.activityInfo.packageName)
        }
        Intent.createChooser(targeted.first(), chooserTitle).apply {
          putExtra(
            Intent.EXTRA_INITIAL_INTENTS,
            targeted.drop(1).toTypedArray(),
          )
        }
      }
    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    return runCatching {
      context.startActivity(launchIntent)
    }.onFailure {
      Log.w(TAG, "Failed to launch email share for export", it)
    }.isSuccess
  }
}
