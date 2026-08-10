package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth.Form

/**
 * Maximum widths for single-pane content on large displays.
 *
 * Screens remain full width on compact devices because [constrainedContentWidth] only applies a
 * maximum width. On wide displays, choose the tier that matches the information density and
 * reading pattern instead of stretching controls and cards across the viewport.
 */
object ContentWidth {

  /**
   * The shell's whole content pane (everything right of the sidebar). Matches the web design's
   * 1200 CSS px content cap; dp ≈ CSS px for layout (see web_adaptive_layout_design.html §11 D3).
   * Screens may still cap themselves narrower inside the pane (e.g. [Form] editors).
   */
  val Pane = 1200.dp

  /** Settings and secondary lists where readable line length matters more than density. */
  val Reading = 960.dp

  /** Editing workflows and focused detail surfaces with stacked fields or action controls. */
  val Form = 720.dp

  /** Authentication and onboarding panels designed as short, focused single-column flows. */
  val Auth = 480.dp
}

/**
 * Expands content to the available width on small screens while centering it within [maxWidth]
 * when a parent supplies horizontal alignment on larger displays.
 */
fun Modifier.constrainedContentWidth(
  maxWidth: Dp = ContentWidth.Reading,
): Modifier = widthIn(max = maxWidth).fillMaxWidth()

/**
 * Aligns app-bar content with a screen's bounded content column on large displays.
 *
 * The outer container keeps the scaffold slot full width, while the rendered bar is constrained
 * to [maxWidth] so navigation and actions line up with the page's content column.
 */
@Composable
fun ConstrainedTopBar(
  maxWidth: Dp = ContentWidth.Reading,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(modifier = Modifier.constrainedContentWidth(maxWidth)) {
      content()
    }
  }
}

/**
 * Positions a floating action at the trailing edge of the same bounded frame as its content.
 */
@Composable
fun ConstrainedFloatingAction(
  maxWidth: Dp = ContentWidth.Reading,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = Alignment.BottomCenter,
  ) {
    Box(
      modifier = Modifier.constrainedContentWidth(maxWidth),
      contentAlignment = Alignment.BottomEnd,
    ) {
      content()
    }
  }
}
