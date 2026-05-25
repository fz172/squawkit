package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.toneFor

/**
 * Compact operational-status label. A shared tier always renders with shared
 * foreground, typography, shape, and spacing across features.
 */
@Composable
fun StatusChip(
  label: String,
  tier: StatusTier,
  modifier: Modifier = Modifier,
) {
  val tone = MaterialTheme.statusColors.toneFor(tier)
  Text(
    text = label.uppercase(),
    style = MaterialTheme.typography.labelSmall.copy(
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    ),
    color = tone.onContainer,
    modifier = modifier
      .background(tone.container, RoundedCornerShape(Spacing.badgeCornerRadius))
      .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
  )
}
