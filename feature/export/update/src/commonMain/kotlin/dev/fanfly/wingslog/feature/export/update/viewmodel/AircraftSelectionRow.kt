package dev.fanfly.wingslog.feature.export.update.viewmodel

/**
 * Display-ready aircraft row used by the export picker.
 */
data class AircraftSelectionRow(
  val aircraftId: String,
  val tailNumber: String,
  val makeModel: String,
  val logCount: Int,
)
