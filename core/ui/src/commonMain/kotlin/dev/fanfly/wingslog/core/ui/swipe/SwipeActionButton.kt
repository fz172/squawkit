package dev.fanfly.wingslog.core.ui.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors

/** Hairline between two bare controls, doing the job the button backgrounds used to. */
@Composable
internal fun ActionDivider() {
  Box(
    modifier = Modifier
      .width(1.dp)
      .height(DividerHeight)
      .background(MaterialTheme.colorScheme.outlineVariant),
  )
}

@Composable
internal fun SwipeActionButton(action: SwipeAction, progress: Float) {
  val tint = when (action.tone) {
    SwipeActionTone.DESTRUCTIVE -> MaterialTheme.colorScheme.error
    SwipeActionTone.POSITIVE -> MaterialTheme.statusColors.positive.accent
  }
  val slide = with(LocalDensity.current) { IconSlideDistance.toPx() }
  Box(
    modifier = Modifier
      .fillMaxHeight()
      .width(ActionWidth)
      .clickable(onClick = action.onClick)
      .padding(vertical = Spacing.small),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.graphicsLayer {
        alpha = ((progress - IconFadeStart) / IconFadeSpan).coerceIn(0f, 1f)
        // Trails the card out rather than sitting waiting for it.
        translationX = slide * (1f - progress)
      },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Icon(
        action.icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(ActionIconSize),
      )
      Text(
        text = action.label,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Clip,
      )
    }
    action.menuContent?.invoke()
  }
}
