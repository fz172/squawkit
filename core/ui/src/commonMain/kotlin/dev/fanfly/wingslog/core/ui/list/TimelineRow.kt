package dev.fanfly.wingslog.core.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography

/**
 * One entry of a dated list drawn as a timeline: a short mono value in the gutter, a dot on a
 * spine that joins adjacent entries, and the entry's own lines. The work logs put a meter reading
 * in the gutter, the data logs a start time.
 *
 * [connectsUp] and [connectsDown] say whether the spine reaches the row directly above or below —
 * a month header or an ad in between breaks it. [lit] is for the newest entry of all.
 *
 * Filled rather than transparent, because a swipe card slides the row over its own controls. No
 * leading inset: the gutter lines up under the section header.
 */
@Composable
fun TimelineRow(
  /** Null for a list with nothing to put there: the gutter closes up and the spine leads the row,
   * inset just past the corner radius. */
  gutter: String?,
  modifier: Modifier = Modifier,
  connectsUp: Boolean = false,
  connectsDown: Boolean = false,
  lit: Boolean = false,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      // Min, so the spine can fill exactly the height the text asks for.
      .height(IntrinsicSize.Min)
      .padding(end = Spacing.large),
  ) {
    if (gutter == null) {
      // A swipe card clips the row to its rounded corners. With no gutter the spine would run
      // through that curve and lose a few pixels at every row boundary, so it starts clear of it.
      Spacer(Modifier.width(Spacing.cardCornerRadius))
    } else {
      Box(
        modifier = Modifier
          .width(rememberTimelineGutterWidth())
          // The end padding is the gap that keeps the digits off the dot.
          .padding(top = Spacing.medium, end = Spacing.small),
      ) {
        Text(
          text = gutter,
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          softWrap = false,
          // Pinned to the spine; a value too long for the gutter grows into the screen's own
          // padding rather than pushing the dots out of line.
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.End, unbounded = true),
        )
      }
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    val dot =
      if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.spineColumn()) {
      // The dot sits level with the first line of text.
      val radius = size.width / 3
      val centre = Offset(
        size.width / 2,
        Spacing.medium.toPx() + Spacing.small.toPx() + radius / 2
      )
      val stroke = Spacing.hairline.toPx()
      if (connectsUp) drawLine(line, Offset(centre.x, 0f), centre, stroke)
      if (connectsDown) drawLine(
        line,
        centre,
        Offset(centre.x, size.height),
        stroke
      )
      drawCircle(dot, radius, centre)
    }
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(vertical = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      content = content,
    )
  }
}

internal fun Modifier.spineColumn(): Modifier =
  padding(end = Spacing.small)
    .width(Spacing.medium)
    .fillMaxHeight()

/** The widest value the gutter holds without overflowing: a five-digit hour meter. */
private const val WIDEST_GUTTER_VALUE = "9999.9"

/**
 * Measured rather than a fixed dp, so [WIDEST_GUTTER_VALUE] fits whatever the font scale — plus a
 * gap so the digits never touch the dot.
 */
@Composable
internal fun rememberTimelineGutterWidth(): Dp {
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val style = WingslogTypography.dataSmall
  return remember(measurer, density, style) {
    with(density) {
      measurer.measure(
        WIDEST_GUTTER_VALUE,
        style,
        maxLines = 1
      ).size.width.toDp()
    }
  } + Spacing.small
}
