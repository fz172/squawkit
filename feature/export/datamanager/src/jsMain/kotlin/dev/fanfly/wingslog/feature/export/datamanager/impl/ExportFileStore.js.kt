package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.w3c.dom.Node
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Give the freshly-clicked download a moment to start before the object URL is revoked.
private const val OBJECT_URL_REVOKE_DELAY_MS = 60_000

/**
 * Web [ExportFileStore]. Browsers have no app-private archive directory, so a finished export is
 * streamed straight to the user's Downloads via a temporary object URL + programmatic anchor click.
 *
 * The history index (export scope/metadata) is persisted in `localStorage`, base64-encoded. Because
 * the browser owns the downloaded file once it lands, the app can't re-read or re-delete it, so
 * [listExports] returns the stored index verbatim and [deleteExport] only forgets the metadata.
 */
@OptIn(ExperimentalEncodingApi::class)
actual class ExportFileStore {

  actual suspend fun writeZip(
    fileName: String,
    bytes: ByteArray
  ): ExportedFile {
    triggerDownload(fileName, bytes)
    return ExportedFile(
      // No durable, app-reachable path exists on the web; the file name is the only stable handle.
      filePath = fileName,
      fileName = fileName,
      displayLocationKind = ExportDisplayLocation.DOWNLOADS_SQUAWKIT,
      sizeBytes = bytes.size.toLong(),
    )
  }

  actual suspend fun saveRecord(ownerUid: String, record: ExportRecord) {
    writeIndex(
      ownerUid,
      ExportRecordManifest.upsert(readIndex(ownerUid), record)
    )
  }

  actual suspend fun listExports(ownerUid: String): List<ExportRecord> =
    readIndex(ownerUid)

  actual suspend fun deleteExport(ownerUid: String, exportId: String): Boolean {
    val stored = readIndex(ownerUid)
    if (stored.none { it.export_id == exportId }) return false
    writeIndex(ownerUid, ExportRecordManifest.remove(stored, exportId))
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

  private fun storageKey(ownerUid: String): String =
    "export_record_index_${ownerUid.replace(Regex("[^A-Za-z0-9._-]"), "_")}"

  private fun readIndex(ownerUid: String): List<ExportRecord> {
    val encoded =
      localStorage.getItem(storageKey(ownerUid)) ?: return emptyList()
    val bytes = runCatching { Base64.decode(encoded) }.getOrNull()
    return ExportRecordManifest.decode(bytes)
  }

  private fun writeIndex(ownerUid: String, records: List<ExportRecord>) {
    localStorage.setItem(
      storageKey(ownerUid),
      Base64.encode(ExportRecordManifest.encode(records))
    )
  }
}
