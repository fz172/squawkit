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
      record("content://a", "A.zip", formats = listOf("PDF", "CSV")),
      record("content://b", "B.zip", formats = listOf("XLSX")),
    )

    val decoded = ExportRecordManifest.decode(ExportRecordManifest.encode(records))

    assertThat(decoded).isEqualTo(records)
  }

  @Test
  fun upsert_replacesByFilePath() {
    val original = record("content://a", "A.zip", formats = listOf("PDF"))
    val replacement = record("content://a", "A.zip", formats = listOf("PDF", "XLSX"))

    val result = ExportRecordManifest.upsert(listOf(original), replacement)

    assertThat(result).containsExactly(replacement)
  }

  @Test
  fun remove_dropsMatchingFilePath() {
    val a = record("content://a", "A.zip")
    val b = record("content://b", "B.zip")

    assertThat(ExportRecordManifest.remove(listOf(a, b), "content://a")).containsExactly(b)
  }

  @Test
  fun reconcile_enrichesDiscoveredAndDropsDeletedAndSortsNewestFirst() {
    val stored = listOf(
      record(
        "content://a", "A.zip",
        formats = listOf("PDF", "XLSX"),
        dateRange = ExportRecordDateRange(kind = "LAST_N_MONTHS", months = 12),
        createdAt = 100L,
        size = 1L,
      ),
      // Manifest for an archive the user deleted outside the app — must not survive.
      record("content://gone", "Gone.zip", formats = listOf("CSV")),
    )
    val discovered = listOf(
      // Same archive, but disk reports the authoritative size/timestamp.
      record("content://a", "A.zip", createdAt = 999L, size = 4_096L),
      // Archive with no manifest (e.g. created before this feature) stays minimal.
      record("content://b", "B.zip", createdAt = 500L, size = 2_048L),
    )

    val result = ExportRecordManifest.reconcile(stored, discovered)

    assertThat(result.map { it.file_path }).containsExactly("content://a", "content://b").inOrder()
    val enriched = result.first { it.file_path == "content://a" }
    assertThat(enriched.formats).containsExactly("PDF", "XLSX").inOrder()
    assertThat(enriched.date_range?.kind).isEqualTo("LAST_N_MONTHS")
    // Volatile facts come from disk.
    assertThat(enriched.size_bytes).isEqualTo(4_096L)
    assertThat(enriched.created_at_epoch_millis).isEqualTo(999L)
    // Unknown archive has no rich scope.
    assertThat(result.first { it.file_path == "content://b" }.formats).isEmpty()
  }

  private fun record(
    filePath: String,
    fileName: String,
    formats: List<String> = emptyList(),
    dateRange: ExportRecordDateRange? = null,
    createdAt: Long = 0L,
    size: Long = 0L,
  ) = ExportRecord(
    file_path = filePath,
    file_name = fileName,
    size_bytes = size,
    created_at_epoch_millis = createdAt,
    formats = formats,
    date_range = dateRange,
  )
}
