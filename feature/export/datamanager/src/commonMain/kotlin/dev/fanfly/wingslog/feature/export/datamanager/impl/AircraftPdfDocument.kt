package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Render-ready document model for a single aircraft export PDF.
 */
data class AircraftPdfDocument(
  val title: String,
  val subtitle: String,
  val summarySections: List<PdfSummarySection>,
  val tableSections: List<PdfTableSection>,
)

data class PdfSummarySection(
  val title: String,
  val cards: List<PdfSummaryCard>,
)

data class PdfSummaryCard(
  val title: String? = null,
  val rows: List<PdfSummaryRow>,
)

data class PdfSummaryRow(
  val label: String,
  val value: String,
)

data class PdfTableSection(
  val title: String,
  val rows: List<List<String>>,
)
