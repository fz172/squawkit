package dev.fanfly.wingslog.core.ui.adaptive.shell.navpill

import androidx.compose.ui.graphics.vector.ImageVector

/** One destination in the [FloatingPillNavigationBar]. */
data class FloatingNavItem(
  /** Resolved, human-readable label. Shown on the selected chip and used as the icon's a11y name. */
  val label: String,
  val icon: ImageVector,
  val selected: Boolean,
  val onClick: () -> Unit,
)
