package dev.fanfly.wingslog.feature.featurelab.datamanager

data class FeatureFlags(
  val technicianEnabled: Boolean = true,
  val attachmentUploadEnabled: Boolean = true,
  val exportEmailDeliveryEnabled: Boolean = false,
  val accountUpgradeEnabled: Boolean = false,
  /**
   * Opt-in for the adaptive web/tablet shell (NavigationSuiteScaffold + aircraft-scoped nav).
   * Default off so the current single-column stack stays intact until parity is proven. See
   * `docs/web/web_adaptive_layout_design.html`.
   */
  val adaptiveShellEnabled: Boolean = false,
)
