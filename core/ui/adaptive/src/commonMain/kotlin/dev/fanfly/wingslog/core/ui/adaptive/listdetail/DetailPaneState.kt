package dev.fanfly.wingslog.core.ui.adaptive.listdetail

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the shell needs to know about an open detail pane: that one is open, so the content column
 * drops its width cap and the pane runs to the window's edge; and how wide it is, so the floating
 * action steps in past it and rides the list rather than the pane's bottom bar. Set by
 * [ListDetailSection], read by the shell.
 */
class DetailPaneState {
  var open: Boolean by mutableStateOf(false)
  var width: Dp by mutableStateOf(0.dp)
}

val LocalDetailPane = compositionLocalOf { DetailPaneState() }
