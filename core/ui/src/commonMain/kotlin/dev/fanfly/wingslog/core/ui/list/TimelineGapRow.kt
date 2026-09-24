package dev.fanfly.wingslog.core.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * Entries a filter removed between two [TimelineRow]s: the spine goes dashed beside [text], so a
 * line spanning six months never implies nothing happened in them.
 */
@Composable
fun TimelineGapRow(text: String, modifier: Modifier = Modifier) {
  val line = MaterialTheme.colorScheme.outline
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .height(IntrinsicSize.Min)
      .padding(end = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(rememberTimelineGutterWidth()))
    Canvas(modifier = Modifier.spineColumn()) {
      val dash = Spacing.extraSmall.toPx()
      drawLine(
        color = line,
        start = Offset(size.width / 2, 0f),
        end = Offset(size.width / 2, size.height),
        strokeWidth = Spacing.hairline.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = Spacing.medium),
    )
  }
}
