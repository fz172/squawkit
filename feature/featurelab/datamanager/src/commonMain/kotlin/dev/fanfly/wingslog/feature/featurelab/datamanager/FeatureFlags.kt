package dev.fanfly.wingslog.feature.featurelab.datamanager

data class FeatureFlags(
  val technicianEnabled: Boolean = true,
  val attachmentUploadEnabled: Boolean = false,
  val exportEmailDeliveryEnabled: Boolean = false,
  val accountUpgradeEnabled: Boolean = false,
)
