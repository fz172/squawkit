package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * One worksheet inside the generated XLSX workbook.
 */
data class XlsxSheet(
  val name: String,
  val rows: List<List<String>>,
)
