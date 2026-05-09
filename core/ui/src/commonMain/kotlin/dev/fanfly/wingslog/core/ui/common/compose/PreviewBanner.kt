package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * Read-only informational banner for summarising a multi-step form's current state.
 *
 * Deliberately styled to look unlike actionable selection cards: left accent stripe only,
 * near-transparent surface, no full border. The shape (4dp left / 12dp right) reinforces that
 * this is a readout, not an input.
 *
 * Use [PreviewBannerTone] to communicate semantic state:
 * - [PreviewBannerTone.Neutral] — nothing configured yet, or no change from default
 * - [PreviewBannerTone.Active] — form has meaningful input; showing a live preview
 * - [PreviewBannerTone.Warn]   — a cycle-skip or destructive adjustment is pending (amber)
 */
enum class PreviewBannerTone { Neutral, Active, Warn }

@Composable
fun PreviewBanner(
  label: String,
  hint: String,
  primary: String,
  secondary: String,
  tone: PreviewBannerTone = PreviewBannerTone.Neutral,
  modifier: Modifier = Modifier,
) {
  val accentColor = when (tone) {
    PreviewBannerTone.Neutral -> MaterialTheme.colorScheme.outline
    PreviewBannerTone.Active -> MaterialTheme.colorScheme.primary
    PreviewBannerTone.Warn -> MaterialTheme.colorScheme.tertiary
  }
  val primaryTextColor = when (tone) {
    PreviewBannerTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
  }
  val bannerShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = Spacing.cardCornerRadius,
    bottomEnd = Spacing.cardCornerRadius,
    bottomStart = 4.dp,
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min)
      .clip(bannerShape)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .width(3.dp)
        .background(accentColor),
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 13.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Text(
          label.uppercase(),
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp,
          ),
          color = accentColor,
        )
        Box(
          modifier = Modifier
            .size(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Text(
          hint,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        primary,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
        ),
        color = primaryTextColor,
      )
      Text(
        secondary,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
