package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * A spreadsheet-shaped table emitted by the logbook export.
 */
data class LogbookExportTable(
  val csvPath: String,
  val sheetName: String,
  val rows: List<List<String>>,
)
