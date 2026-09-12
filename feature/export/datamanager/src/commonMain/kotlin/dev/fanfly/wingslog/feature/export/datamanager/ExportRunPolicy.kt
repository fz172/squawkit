package dev.fanfly.wingslog.feature.export.datamanager

/**
 * How an in-flight export relates to the app's lifecycle on this platform (#343).
 *
 * - [stopWhenBackgrounded]: iOS suspends a backgrounded app within seconds, so an export that kept
 *   running there would stall on the upload step and be killed at the OS's discretion. Stopping it
 *   deliberately and asking the user to start again is the honest behaviour.
 * - [survivesLeavingScreen]: Android runs the export as a WorkManager job with a foreground
 *   notification, so leaving the screen (or the app) does not touch it. Elsewhere the job is bound
 *   to the export screen and cancelled when it goes away.
 */
data class ExportRunPolicy(
  val stopWhenBackgrounded: Boolean,
  val survivesLeavingScreen: Boolean,
)
