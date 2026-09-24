package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SeriesDrag
import kotlin.math.roundToInt

/** The series chip in flight, following the pointer above every pane. */
@Composable
internal fun DragChip(drag: SeriesDrag, boxOrigin: Offset) {
  val local = drag.position - boxOrigin
  Surface(
  shape = RoundedCornerShape(Spacing.smallCornerRadius),
  color = MaterialTheme.colorScheme.surfaceContainerHighest,
  tonalElevation = Spacing.extraSmall,
  shadowElevation = Spacing.extraSmall,
  modifier = Modifier.offset {
    IntOffset(
      local.x.roundToInt(),
      local.y.roundToInt()
    )
  },
) {
  Text(
    drag.label,
    style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.padding(
      horizontal = Spacing.medium,
      vertical = Spacing.small
    ),
  )
}
}
