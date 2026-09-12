package dev.fanfly.wingslog.feature.export.datamanager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A tapped export notification asks for the export screen (#343). Same shape as
 * `ThingShareDeepLinks`: the host's launch-intent handler parks the request here, and a root-level
 * composable navigates once the shell is on the back stack, so a cold start and a warm tap behave
 * the same.
 */
object ExportDeepLinks {
  const val OPEN_URI = "wingslog://export"

  private val _pendingOpen = MutableStateFlow(false)
  val pendingOpen: StateFlow<Boolean> = _pendingOpen.asStateFlow()

  /** Parks the request if [uri] is ours; returns whether it was taken. */
  fun deliver(uri: String): Boolean {
    if (uri != OPEN_URI) return false
    _pendingOpen.value = true
    return true
  }

  fun consume() {
    _pendingOpen.value = false
  }
}
