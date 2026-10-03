package dev.fanfly.wingslog.core.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the device has a network connection now. A hint for the UI, not a promise: "online" can
 * still fail (a captive portal, a dead backend), so a caller still handles the call failing. What it
 * buys is not offering an online-only action offline (PRD R51).
 */
interface ConnectivityMonitor {
  val isOnline: StateFlow<Boolean>
}
