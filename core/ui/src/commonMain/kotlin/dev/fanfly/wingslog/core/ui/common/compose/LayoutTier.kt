package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window-width tiers that drive the adaptive shell.
 *
 * The thresholds mirror the breakpoints in the `Hopply Web` design prototype (720 / 1040 / 1180 CSS
 * px), expressed here as dp. They are intentionally coarser than Material3's default
 * `WindowWidthSizeClass` buckets (600 / 840), which do not line up with where the prototype actually
 * switches its navigation container and dashboard rail. See
 * `docs/web/web_adaptive_layout_design.html` §4.2.
 *
 * - [COMPACT]  — phone: bottom navigation bar, single column, bottom sheets / full-screen overlays.
 * - [MEDIUM]   — tablet portrait: navigation rail, 1–2 column grids, side drawers; no dashboard rail.
 * - [EXPANDED] — tablet landscape / small laptop: permanent sidebar, 2-column grids; no dashboard rail.
 * - [LARGE]    — desktop: sidebar + sticky dashboard rail, multi-pane, width-capped content.
 */
enum class LayoutTier {
  COMPACT,
  MEDIUM,
  EXPANDED,
  LARGE,
  ;

  /** True for phone-sized windows that use the single-column / bottom-bar chrome. */
  val isCompact: Boolean get() = this == COMPACT

  /** True once there is room for a persistent side navigation container (rail or sidebar). */
  val hasSideNav: Boolean get() = this != COMPACT

  /** True when the layout is wide enough to show the full-width sidebar rather than the icon rail. */
  val hasFullSidebar: Boolean get() = this == EXPANDED || this == LARGE

  /** True only on the widest tier, where the dashboard shows its main column + sticky side rail. */
  val hasDashboardRail: Boolean get() = this == LARGE

  /** Number of columns to use for browseable card grids (tasks, squawks, aircraft). */
  val cardColumns: Int get() = if (this == EXPANDED || this == LARGE) 2 else 1
}

/**
 * The current [LayoutTier], published by the adaptive shell so overlays (drawers, sheets, dialogs)
 * that render in their own window — outside the shell's `BoxWithConstraints` — can still pick a
 * tier-appropriate presentation. Defaults to [LayoutTier.COMPACT] so any surface shown outside the
 * shell (e.g. the legacy stack) keeps its phone-style behavior.
 */
val LocalLayoutTier = compositionLocalOf { LayoutTier.COMPACT }

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

/**
 * The current [LayoutTier] for the active window, from [LocalWindowInfo]'s container size.
 *
 * **Caveat:** on Kotlin/JS this does not reliably report the window width, which collapses wide web
 * windows to [LayoutTier.COMPACT]. For layout decisions prefer wrapping content in
 * `BoxWithConstraints` and calling [layoutTierFor]`(maxWidth)`, which uses the measured constraints
 * and is correct on every platform (this is what `AdaptiveAppShell` does). This helper remains for
 * Android/iOS callers that need a window-level tier outside a layout scope.
 */
@Composable
fun rememberLayoutTier(): LayoutTier {
  val windowInfo = LocalWindowInfo.current
  val density = LocalDensity.current
  val widthPx = windowInfo.containerSize.width
  return remember(widthPx, density.density) {
    val widthDp = with(density) { widthPx.toDp() }
    layoutTierFor(widthDp)
  }
}
