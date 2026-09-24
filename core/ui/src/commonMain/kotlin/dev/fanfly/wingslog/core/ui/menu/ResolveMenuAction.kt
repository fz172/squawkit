package dev.fanfly.wingslog.core.ui.menu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** One row of a [ResolveBubbleMenu]. */
data class ResolveMenuAction(
  val icon: ImageVector,
  val iconBackground: Color,
  val iconTint: Color,
  val label: String,
  /** Optional second line explaining what the option does. */
  val subtitle: String? = null,
  val onClick: () -> Unit,
)
