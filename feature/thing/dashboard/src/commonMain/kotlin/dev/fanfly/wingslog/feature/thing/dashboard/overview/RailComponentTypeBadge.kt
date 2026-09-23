package dev.fanfly.wingslog.feature.thing.dashboard.overview

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.thing.ComponentType

/** Wide enough for the longest pill, "PROPELLER". */
private val RAIL_BADGE_WIDTH = 88.dp

@Composable
internal fun RailComponentTypeBadge(
  type: ComponentType,
  modifier: Modifier = Modifier
) {
  val (background, content) = when (type) {
    ComponentType.COMPONENT_ENGINE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    ComponentType.COMPONENT_AIRFRAME -> MaterialTheme.statusColors.positive.container to MaterialTheme.statusColors.positive.onContainer
    ComponentType.COMPONENT_PROPELLER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
  }
  // Fixed width so the titles beside pills of different lengths line up down the list.
  Surface(
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
    color = background,
    modifier = modifier.width(RAIL_BADGE_WIDTH),
  ) {
    Text(
      text = type.displayName()
        .uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = content,
      textAlign = TextAlign.Center,
      maxLines = 1,
      modifier = Modifier.padding(
        horizontal = Spacing.small,
        vertical = Spacing.extraSmall
      ),
    )
  }
}
