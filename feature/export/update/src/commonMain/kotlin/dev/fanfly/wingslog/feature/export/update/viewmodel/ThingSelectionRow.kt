package dev.fanfly.wingslog.feature.export.update.viewmodel

/**
 * Display-ready thing row used by the export picker.
 */
data class ThingSelectionRow(
  val thingId: String,
  val tailNumber: String,
  val makeModel: String,
  val logCount: Int,
  val attachmentSizeBytes: Long,
)
