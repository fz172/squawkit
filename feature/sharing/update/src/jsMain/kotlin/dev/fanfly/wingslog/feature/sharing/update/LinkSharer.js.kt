package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
actual fun rememberLinkSharer(): LinkSharer = remember { WebLinkSharer() }

private class WebLinkSharer : LinkSharer {
  override fun shareLink(url: String, chooserTitle: String): Boolean {
    // Use the Web Share API where available (mobile browsers); otherwise fall back to copying the
    // link to the clipboard so the user can paste it into a message.
    val navigator = window.navigator.asDynamic()
    return runCatching {
      if (navigator.share != undefined) {
        navigator.share(shareData(url))
      } else {
        navigator.clipboard.writeText(url)
      }
      true
    }.getOrDefault(false)
  }
}

private fun shareData(url: String): dynamic {
  val data = js("({})")
  data.url = url
  return data
}
