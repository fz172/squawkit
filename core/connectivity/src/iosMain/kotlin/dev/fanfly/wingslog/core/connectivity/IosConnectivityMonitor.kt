package dev.fanfly.wingslog.core.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * The network path's state, from Network.framework's path monitor, for the life of the process.
 * Starts optimistic: the monitor reports the current path at once, and an action offered for that
 * moment still handles a failed call.
 */
internal class IosConnectivityMonitor : ConnectivityMonitor {

  private val online = MutableStateFlow(true)
  override val isOnline: StateFlow<Boolean> = online.asStateFlow()

  private val monitor = nw_path_monitor_create().also { monitor ->
    nw_path_monitor_set_update_handler(monitor) { path ->
      online.value = nw_path_get_status(path) == nw_path_status_satisfied
    }
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
  }
}
