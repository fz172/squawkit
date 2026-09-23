package dev.fanfly.wingslog.core.ui.adaptive.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Inclusive lower bounds for each tier, in dp. Tunable; verify on real tablets (see design §11 D3). */
object LayoutBreakpoints {
  val mediumMin: Dp = 700.dp
  val expandedMin: Dp = 1040.dp
  val largeMin: Dp = 1180.dp
}

/**
 * Pure mapping from a window width to its [LayoutTier]. Extracted from the composable so the tier
 * logic can be unit-tested without a Compose host.
 */
fun layoutTierFor(widthDp: Dp): LayoutTier = when {
  widthDp < LayoutBreakpoints.mediumMin -> LayoutTier.COMPACT
  widthDp < LayoutBreakpoints.expandedMin -> LayoutTier.MEDIUM
  widthDp < LayoutBreakpoints.largeMin -> LayoutTier.EXPANDED
  else -> LayoutTier.LARGE
}
