package dev.fanfly.wingslog.feature.export.datamanager

enum class ExportDeliveryEmailSource {
  EXPLICIT,
  AUTH_FALLBACK,
}

data class ExportDeliveryInfo(
  val destinationEmail: String,
  val source: ExportDeliveryEmailSource,
)
