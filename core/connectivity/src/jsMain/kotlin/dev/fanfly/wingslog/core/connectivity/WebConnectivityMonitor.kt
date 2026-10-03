package dev.fanfly.wingslog.core.connectivity

import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The browser's `navigator.onLine`, kept current by its `online` and `offline` events. */
internal class WebConnectivityMonitor : ConnectivityMonitor {

  private val online = MutableStateFlow(window.navigator.onLine)
  override val isOnline: StateFlow<Boolean> = online.asStateFlow()

  init {
    window.addEventListener("online", { online.value = true })
    window.addEventListener("offline", { online.value = false })
  }
}
