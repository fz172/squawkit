package dev.fanfly.wingslog.feature.export.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.w3c.dom.Node

// Give the freshly-clicked download a moment to start before the object URL is revoked.
private const val OBJECT_URL_REVOKE_DELAY_MS = 60_000

@Composable
actual fun rememberExportFileDownloader(): ExportFileDownloader =
  remember { WebExportFileDownloader() }

private class WebExportFileDownloader : ExportFileDownloader {

  override suspend fun download(
    filePath: String,
    fileName: String,
    fetchBytes: suspend () -> ByteArray?,
  ): Boolean {
    val bytes = fetchBytes() ?: return false
    triggerDownload(fileName, bytes)
    return true
  }

  private fun triggerDownload(fileName: String, bytes: ByteArray) {
    val data = Uint8Array(bytes.toTypedArray())
    val type = "application/zip"
    val url =
      js("URL.createObjectURL(new Blob([data], { type: type }))").unsafeCast<String>()
    val anchor = document.createElement("a")
      .asDynamic()
    anchor.href = url
    anchor.download = fileName
    document.body?.appendChild(anchor.unsafeCast<Node>())
    anchor.click()
    document.body?.removeChild(anchor.unsafeCast<Node>())
    window.setTimeout(
      { js("URL.revokeObjectURL(url)"); Unit },
      OBJECT_URL_REVOKE_DELAY_MS
    )
  }
}
