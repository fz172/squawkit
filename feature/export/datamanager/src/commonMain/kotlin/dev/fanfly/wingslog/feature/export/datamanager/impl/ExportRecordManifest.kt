package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordIndex

/**
 * Pure helpers for the export-metadata index that backs export history.
 *
 * The index is a serialized [ExportRecordIndex] kept in app-private storage. Each platform
 * [ExportFileStore] owns the byte IO; this object owns the encode/decode and reconciliation logic
 * so both platforms behave identically.
 *
 * Reconciliation treats the on-disk archives as the source of truth for *existence* and volatile
 * facts (path, size, timestamp), and the stored manifest as the source of truth for the rich scope
 * (formats, date range, aircraft) that cannot be recovered from the file system alone.
 */
internal object ExportRecordManifest {

  private const val LEGACY_EXPORT_ID_PREFIX = "legacy:"

  /** Decodes the persisted index, tolerating absent or corrupt bytes by returning an empty list. */
  fun decode(bytes: ByteArray?): List<ExportRecord> =
    bytes?.let { runCatching { ExportRecordIndex.ADAPTER.decode(it) }.getOrNull() }
      ?.records
      ?.map(::ensureExportId)
      .orEmpty()

  fun encode(records: List<ExportRecord>): ByteArray =
    ExportRecordIndex(records = records.map(::ensureExportId)).encode()

  /** Inserts or replaces [record] (keyed by `export_id`) in [stored]. */
  fun upsert(stored: List<ExportRecord>, record: ExportRecord): List<ExportRecord> =
    stored.filterNot { keyOf(it) == keyOf(record) } + ensureExportId(record)

  fun remove(stored: List<ExportRecord>, exportId: String): List<ExportRecord> =
    stored.filterNot { keyOf(it) == exportId }

  /**
   * Merges stored manifests onto the archives actually present on disk.
   *
   * The result mirrors [discovered] exactly (so deleted archives drop out and unknown archives are
   * still listed with minimal metadata), enriched with the manifest's scope where one exists.
   * Returned newest-first; persist it back as the new index to self-heal stale entries.
   */
  fun reconcile(
    stored: List<ExportRecord>,
    discovered: List<ExportRecord>,
  ): List<ExportRecord> {
    val manifestByPath = stored.map(::ensureExportId).associateBy { it.file_path }
    return discovered
      .map { diskRecord ->
        val disk = ensureExportId(diskRecord)
        val manifest = manifestByPath[disk.file_path] ?: return@map disk
        manifest.copy(
          export_id = manifest.export_id.ifBlank { disk.export_id },
          file_path = disk.file_path,
          file_name = disk.file_name.ifBlank { manifest.file_name },
          size_bytes = disk.size_bytes,
          created_at_epoch_millis =
            if (disk.created_at_epoch_millis > 0L) disk.created_at_epoch_millis
            else manifest.created_at_epoch_millis,
          display_location = disk.display_location.ifBlank { manifest.display_location },
        )
      }
      .sortedByDescending { it.created_at_epoch_millis }
  }

  private fun ensureExportId(record: ExportRecord): ExportRecord =
    if (record.export_id.isNotBlank()) record
    else record.copy(export_id = legacyExportId(record.file_path))

  private fun legacyExportId(filePath: String): String = "$LEGACY_EXPORT_ID_PREFIX$filePath"

  private fun keyOf(record: ExportRecord): String = ensureExportId(record).export_id
}
