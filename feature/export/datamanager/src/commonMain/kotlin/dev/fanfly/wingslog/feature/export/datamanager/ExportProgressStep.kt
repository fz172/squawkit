package dev.fanfly.wingslog.feature.export.datamanager

/**
 * Stable export progress stages that the UI maps to localized user-facing text.
 */
enum class ExportProgressStep {
  COLLECTING_DATA,
  BUILDING_ARCHIVE,
  COMPRESSING_ARCHIVE,
  SAVING_FILE,
  UPLOADING_ARCHIVE,
  REQUESTING_DELIVERY,
}
