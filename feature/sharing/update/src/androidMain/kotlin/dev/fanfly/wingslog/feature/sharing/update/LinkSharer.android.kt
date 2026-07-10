package dev.fanfly.wingslog.feature.sharing.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberLinkSharer(): LinkSharer {
  val context = LocalContext.current
  return remember(context) { AndroidLinkSharer(context.applicationContext) }
}

private class AndroidLinkSharer(private val context: Context) : LinkSharer {
  override fun shareLink(url: String, chooserTitle: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, url)
    }
    val chooser = Intent.createChooser(send, chooserTitle)
      .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return runCatching { context.startActivity(chooser) }.isSuccess
  }
}
