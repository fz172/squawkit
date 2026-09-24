package dev.fanfly.wingslog.core.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Caps content to [maxWidth] on wide displays while letting it fill the available width below it.
 *
 * This does **not** centre the caller — a child sits at the box's start edge unless centring is
 * supplied. On wide layouts wrap it in a container that carries the horizontal alignment, e.g.
 * `Box(contentAlignment = Alignment.TopCenter) { … }` (see [ConstrainedTopBar] and
 * [ConstrainedFloatingAction], which already show that pattern). Screens that forget the
 * alignment pin an otherwise cap-sized column to the left edge at desktop width.
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
