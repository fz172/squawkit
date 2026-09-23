package dev.fanfly.wingslog.core.ui.adaptive.layout

import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.layout.ContentWidth.Form

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

  /** Choice dialogs that replace a phone bottom sheet on wider tiers. */
  val Dialog = 560.dp
}
