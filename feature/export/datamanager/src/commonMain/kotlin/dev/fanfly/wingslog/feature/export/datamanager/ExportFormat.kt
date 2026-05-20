package dev.fanfly.wingslog.feature.export.datamanager

/**
 * Report document types that can be bundled inside an export ZIP.
 *
 * Every export ships as a ZIP; the selected formats decide which report documents are written
 * for each aircraft. Attachments and the README are always included regardless of selection.
 *
 * Declaration order is the canonical display order.
 */
enum class ExportFormat {
  PDF,
  CSV,
  XLSX,
  ;

  companion object {
    /** All formats, used as the default when a request does not narrow the selection. */
    val ALL: Set<ExportFormat> = entries.toSet()
  }
}
