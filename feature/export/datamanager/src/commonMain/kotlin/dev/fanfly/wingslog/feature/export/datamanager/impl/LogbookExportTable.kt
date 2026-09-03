package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * A spreadsheet-shaped table emitted by the export.
 */
data class LogbookExportTable(
  val csvPath: String,
  val sheetName: String,
  val rows: List<List<String>>,
  /**
   * Whether the PDF renders this table.
   *
   * A flag rather than the PDF matching on file names: the generic layout names its tables from
   * the lexicon, so "which tables are reference data" stops being answerable from the path.
   */
  val includeInPdf: Boolean = true,
)
