package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
actual fun rememberExportFileSharer(): ExportFileSharer =
  remember { WebExportFileSharer() }

private class WebExportFileSharer : ExportFileSharer {
  override fun share(
    filePath: String,
    chooserTitle: String,
    subject: String,
    body: String,
  ): Boolean {
    // Browsers can't attach a file to an outgoing email, and the archive has already been streamed
    // to the user's Downloads by ExportFileStore. The closest equivalent is to open a prefilled mail
    // draft (subject + body) the user can attach the downloaded file to.
    val href = "mailto:?subject=${encodeUriComponent(subject)}&body=${
      encodeUriComponent(body)
    }"
    return runCatching {
      window.location.href = href
      true
    }.getOrDefault(false)
  }
}

private fun encodeUriComponent(value: String): String =
  js("encodeURIComponent(value)").unsafeCast<String>()
