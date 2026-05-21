package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordDateRange
import org.junit.Test

class ExportRecordManifestTest {

  @Test
  fun decode_handlesAbsentAndCorruptBytes() {
    assertThat(ExportRecordManifest.decode(null)).isEmpty()
    assertThat(ExportRecordManifest.decode(byteArrayOf(1, 2, 3, 4))).isEmpty()
  }

  @Test
  fun encodeDecode_roundTripsRecords() {
    val records = listOf(
      record("export-a", "content://a", "A.zip", formats = listOf("PDF", "CSV")),
      record("export-b", "content://b", "B.zip", formats = listOf("XLSX")),
    )

    val decoded = ExportRecordManifest.decode(ExportRecordManifest.encode(records))

    assertThat(decoded).isEqualTo(records)
  }

  @Test
  fun upsert_replacesByExportId() {
    val original = record("export-a", "content://a", "A.zip", formats = listOf("PDF"))
    val replacement = record("export-a", "content://b", "A.zip", formats = listOf("PDF", "XLSX"))

    val result = ExportRecordManifest.upsert(listOf(original), replacement)

    assertThat(result).containsExactly(replacement)
  }

  @Test
  fun remove_dropsMatchingExportId() {
    val a = record("export-a", "content://a", "A.zip")
    val b = record("export-b", "content://b", "B.zip")

    assertThat(ExportRecordManifest.remove(listOf(a, b), "export-a")).containsExactly(b)
  }

  @Test
  fun decode_dropsRecordsWithoutExportId() {
    val bytes = ExportRecordManifest.encode(
      listOf(
        record("export-a", "content://a", "A.zip"),
        record("", "content://b", "B.zip"),
      ),
    )

    assertThat(ExportRecordManifest.decode(bytes)).containsExactly(record("export-a", "content://a", "A.zip"))
  }

  @Test
  fun reconcile_enrichesKnownDiscoveredAndDropsDeletedAndUnknownArchives() {
    val stored = listOf(
      record(
        "export-a", "content://a", "A.zip",
        formats = listOf("PDF", "XLSX"),
        dateRange = ExportRecordDateRange(kind = "LAST_N_MONTHS", months = 12),
        createdAt = 100L,
        size = 1L,
      ),
      // Manifest for an archive the user deleted outside the app — must not survive.
      record("export-gone", "content://gone", "Gone.zip", formats = listOf("CSV")),
    )
    val discovered = listOf(
      // Same archive, but disk reports the authoritative size/timestamp.
      record("", "content://a", "A.zip", createdAt = 999L, size = 4_096L),
      // Archive with no manifest is ignored.
      record("", "content://b", "B.zip", createdAt = 500L, size = 2_048L),
    )

    val result = ExportRecordManifest.reconcile(stored, discovered)

    assertThat(result.map { it.file_path }).containsExactly("content://a")
    val enriched = result.first { it.file_path == "content://a" }
    assertThat(enriched.export_id).isEqualTo("export-a")
    assertThat(enriched.formats).containsExactly("PDF", "XLSX").inOrder()
    assertThat(enriched.date_range?.kind).isEqualTo("LAST_N_MONTHS")
    // Volatile facts come from disk.
    assertThat(enriched.size_bytes).isEqualTo(4_096L)
    assertThat(enriched.created_at_epoch_millis).isEqualTo(999L)
  }

  private fun record(
    exportId: String,
    filePath: String,
    fileName: String,
    formats: List<String> = emptyList(),
    dateRange: ExportRecordDateRange? = null,
    createdAt: Long = 0L,
    size: Long = 0L,
  ) = ExportRecord(
    export_id = exportId,
    file_path = filePath,
    file_name = fileName,
    size_bytes = size,
    created_at_epoch_millis = createdAt,
    formats = formats,
    date_range = dateRange,
  )
}
